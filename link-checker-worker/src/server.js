import http from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import { config } from './config.js';
import { validateCheckUrl, UrlValidationError } from './url-validator.js';
import { checkLink, getDomainCooldownResult } from './check-link.js';
import { closeBrowser } from './browser-manager.js';
import { QueueFullError, SingleFlightQueue } from './queue.js';
import { readJsonBody, RequestBodyTooLargeError } from './request-body.js';

const queue = new SingleFlightQueue(config.maxQueueSize);

if (!config.workerToken) {
  throw new Error('LINK_CHECKER_WORKER_TOKEN must be configured');
}

function isAuthorized(request) {
  const authorization = request.headers.authorization;
  const prefix = 'Bearer ';
  if (typeof authorization !== 'string' || !authorization.startsWith(prefix)) {
    return false;
  }
  const supplied = Buffer.from(authorization.slice(prefix.length), 'utf8');
  const expected = Buffer.from(config.workerToken, 'utf8');
  return supplied.length === expected.length && timingSafeEqual(supplied, expected);
}

function requiresAuthorization(request) {
  return (request.method === 'GET' && request.url === '/health')
    || (request.method === 'POST' && request.url === '/check');
}

function sendJson(response, statusCode, body) {
  const payload = JSON.stringify(body);
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  });
  response.end(payload);
}

const server = http.createServer(async (request, response) => {
  try {
    if (requiresAuthorization(request) && !isAuthorized(request)) {
      sendJson(response, 401, { error: 'Unauthorized' });
      return;
    }

    if (request.method === 'GET' && request.url === '/health') {
      sendJson(response, 200, { status: 'UP' });
      return;
    }

    if (request.method === 'POST' && request.url === '/check') {
      let body;
      try {
        body = await readJsonBody(request, config.maxRequestBodyBytes);
      } catch (error) {
        if (error instanceof RequestBodyTooLargeError) {
          sendJson(response, 413, { error: 'Request body too large', reason: 'REQUEST_BODY_TOO_LARGE' });
          return;
        }
        if (error instanceof Error && error.message === 'INVALID_JSON') {
          sendJson(response, 400, { error: 'Invalid JSON body', reason: 'INVALID_JSON' });
          return;
        }
        throw error;
      }

      if (body === null || typeof body !== 'object' || Array.isArray(body)) {
        sendJson(response, 400, { error: 'URL rejected', reason: 'INVALID_URL' });
        return;
      }

      let validatedUrl;
      try {
        validatedUrl = await validateCheckUrl(body.url);
      } catch (error) {
        if (error instanceof UrlValidationError) {
          sendJson(response, error.statusCode, { error: 'URL rejected', reason: error.reason });
          return;
        }
        throw error;
      }

      const cooldownResult = getDomainCooldownResult(validatedUrl);
      if (cooldownResult) {
        sendJson(response, 200, cooldownResult);
        return;
      }

      try {
        if (queue.isFull()) {
          throw new QueueFullError();
        }
        const result = await queue.enqueue(() => checkLink(validatedUrl));
        sendJson(response, 200, result);
      } catch (error) {
        if (error instanceof QueueFullError) {
          sendJson(response, 503, { error: 'Worker queue unavailable', reason: 'QUEUE_FULL' });
          return;
        }
        if (error instanceof UrlValidationError) {
          sendJson(response, error.statusCode, { error: 'Navigation rejected', reason: error.reason });
          return;
        }
        throw error;
      }
      return;
    }

    sendJson(response, 404, { error: 'Not found' });
  } catch {
    sendJson(response, 503, {
      error: 'Worker technical error',
      reason: 'INFRASTRUCTURE_ERROR',
    });
  }
});

function shutdown(signal) {
  console.log(`Received ${signal}, shutting down...`);
  server.close(async () => {
    await closeBrowser();
    process.exit(0);
  });
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));

server.listen(config.port, () => {
  console.log(`link-checker-worker listening on port ${config.port}`);
});
