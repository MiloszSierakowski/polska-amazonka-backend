import http from 'node:http';
import { config } from './config.js';
import { validateCheckUrl } from './url-validator.js';
import { checkLink } from './check-link.js';
import { closeBrowser } from './browser-manager.js';
import { SingleFlightQueue } from './queue.js';

const queue = new SingleFlightQueue();

function sendJson(response, statusCode, body) {
  const payload = JSON.stringify(body);
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  });
  response.end(payload);
}

async function readJsonBody(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  if (chunks.length === 0) {
    return {};
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new Error('INVALID_JSON');
  }
}

const server = http.createServer(async (request, response) => {
  try {
    if (request.method === 'GET' && request.url === '/health') {
      sendJson(response, 200, { status: 'UP' });
      return;
    }

    if (request.method === 'POST' && request.url === '/check') {
      let body;
      try {
        body = await readJsonBody(request);
      } catch (error) {
        if (error instanceof Error && error.message === 'INVALID_JSON') {
          sendJson(response, 400, { error: 'Invalid JSON body' });
          return;
        }
        throw error;
      }

      const validation = validateCheckUrl(body.url);
      if (!validation.ok) {
        sendJson(response, 400, { error: validation.reason });
        return;
      }

      const result = await queue.enqueue(() => checkLink(validation.url));
      sendJson(response, 200, result);
      return;
    }

    sendJson(response, 404, { error: 'Not found' });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    sendJson(response, 503, {
      error: 'Worker technical error',
      reason: message,
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
