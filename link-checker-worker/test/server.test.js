import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');

function request(port, method, urlPath, body) {
  return new Promise((resolve, reject) => {
    const payload = body ? JSON.stringify(body) : undefined;
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port,
        path: urlPath,
        method,
        headers: payload
          ? {
              'Content-Type': 'application/json',
              'Content-Length': Buffer.byteLength(payload),
            }
          : undefined,
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
    if (payload) {
      req.write(payload);
    }
    req.end();
  });
}

async function waitForHealth(port, timeoutMs = 5000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    try {
      const health = await request(port, 'GET', '/health');
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
    env: { ...process.env, PORT: String(port) },
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

  const health = await request(port, 'GET', '/health');
  assert.equal(health.statusCode, 200);
  assert.equal(health.body.status, 'UP');

  const missingUrl = await request(port, 'POST', '/check', {});
  assert.equal(missingUrl.statusCode, 400);

  const invalidUrl = await request(port, 'POST', '/check', { url: 'ftp://bad.example' });
  assert.equal(invalidUrl.statusCode, 400);
});
