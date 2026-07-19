import test from 'node:test';
import assert from 'node:assert/strict';
import { createBrowserLifecycle } from '../src/browser-manager.js';

function fakeBrowser() {
  const listeners = new Map();
  let connected = true;
  let closeCalls = 0;
  return {
    on: (event, listener) => listeners.set(event, listener),
    isConnected: () => connected,
    close: async () => {
      closeCalls += 1;
      connected = false;
    },
    disconnect: () => {
      connected = false;
      listeners.get('disconnected')?.();
    },
    closeCalls: () => closeCalls,
  };
}

test('failed browser launch is cleared and the next getBrowser retries', async () => {
  const browser = fakeBrowser();
  let launches = 0;
  const lifecycle = createBrowserLifecycle(async () => {
    launches += 1;
    if (launches === 1) {
      throw new Error('launch failed');
    }
    return browser;
  });

  await assert.rejects(lifecycle.getBrowser());
  assert.equal(await lifecycle.getBrowser(), browser);
  assert.equal(launches, 2);
});

test('disconnect clears the browser and a later request launches a new one', async () => {
  const first = fakeBrowser();
  const second = fakeBrowser();
  const browsers = [first, second];
  let launches = 0;
  const lifecycle = createBrowserLifecycle(async () => browsers[launches++]);

  assert.equal(await lifecycle.getBrowser(), first);
  first.disconnect();
  assert.equal(await lifecycle.getBrowser(), second);
  assert.equal(launches, 2);
});

test('closeBrowser is safe after a failed launch and for a disconnected browser', async () => {
  const failedLifecycle = createBrowserLifecycle(async () => {
    throw new Error('launch failed');
  });
  await assert.rejects(failedLifecycle.getBrowser());
  await assert.doesNotReject(failedLifecycle.closeBrowser());

  const browser = fakeBrowser();
  const disconnectedLifecycle = createBrowserLifecycle(async () => browser);
  await disconnectedLifecycle.getBrowser();
  browser.disconnect();
  await assert.doesNotReject(disconnectedLifecycle.closeBrowser());
  assert.equal(browser.closeCalls(), 0);
});
