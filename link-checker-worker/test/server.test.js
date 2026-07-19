import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');

const workerToken = 'test-worker-token';

function request(port, method, urlPath, body, token) {
  return new Promise((resolve, reject) => {
    const payload = body !== undefined ? JSON.stringify(body) : undefined;
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port,
        path: urlPath,
        method,
        headers: {
          ...(payload !== undefined
            ? {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload),
              }
            : {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      },
      (res) => {
        const chunks = [];
        res.on('data', (chunk) => chunks.push(chunk));
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString('utf8');
          resolve({
            statusCode: res.statusCode,
            body: text ? JSON.parse(text) : null,
          });
        });
      },
    );
    req.on('error', reject);
    if (payload !== undefined) {
      req.write(payload);
    }
    req.end();
  });
}

async function waitForHealth(port, timeoutMs = 5000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    try {
      const health = await request(port, 'GET', '/health', undefined, workerToken);
      if (health.statusCode === 200) {
        return;
      }
    } catch {
      // Server still starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Worker did not become ready on port ${port}`);
}

test('server exposes health and validates check payload', async (t) => {
  const port = 3101 + Math.floor(Math.random() * 100);
  const child = spawn(process.execPath, ['src/server.js'], {
    cwd: workerRoot,
    env: { ...process.env, PORT: String(port), LINK_CHECKER_WORKER_TOKEN: workerToken },
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  let stderr = '';
  child.stderr.on('data', (chunk) => {
    stderr += chunk.toString('utf8');
  });

  t.after(async () => {
    child.kill('SIGTERM');
    await new Promise((resolve) => child.on('exit', resolve));
  });

  try {
    await waitForHealth(port);
  } catch (error) {
    throw new Error(`${error instanceof Error ? error.message : String(error)}\n${stderr}`);
  }

  const unauthorizedHealth = await request(port, 'GET', '/health');
  assert.equal(unauthorizedHealth.statusCode, 401);

  const wrongHealth = await request(port, 'GET', '/health', undefined, 'wrong-token');
  assert.equal(wrongHealth.statusCode, 401);

  const health = await request(port, 'GET', '/health', undefined, workerToken);
  assert.equal(health.statusCode, 200);
  assert.equal(health.body.status, 'UP');

  const unauthorizedCheck = await request(port, 'POST', '/check', {});
  assert.equal(unauthorizedCheck.statusCode, 401);

  const wrongCheck = await request(port, 'POST', '/check', {}, 'wrong-token');
  assert.equal(wrongCheck.statusCode, 401);

  const missingUrl = await request(port, 'POST', '/check', {}, workerToken);
  assert.equal(missingUrl.statusCode, 400);

  const invalidUrl = await request(port, 'POST', '/check', { url: 'ftp://bad.example' }, workerToken);
  assert.equal(invalidUrl.statusCode, 400);

  const nullBody = await request(port, 'POST', '/check', null, workerToken);
  assert.equal(nullBody.statusCode, 400);
  assert.equal(nullBody.body.reason, 'INVALID_URL');

  const oversized = await request(port, 'POST', '/check', { url: 'x'.repeat(20000) }, workerToken);
  assert.equal(oversized.statusCode, 413);
});
