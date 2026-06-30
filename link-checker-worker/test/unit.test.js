import test from 'node:test';
import assert from 'node:assert/strict';
import { validateCheckUrl } from '../src/url-validator.js';
import { classifyContent } from '../src/check-link.js';
import { SingleFlightQueue } from '../src/queue.js';

test('validateCheckUrl accepts http and https', () => {
  assert.equal(validateCheckUrl('https://example.com/product').ok, true);
  assert.equal(validateCheckUrl('http://example.com/product').ok, true);
});

test('validateCheckUrl rejects missing or invalid url', () => {
  assert.equal(validateCheckUrl('').ok, false);
  assert.equal(validateCheckUrl('ftp://example.com').ok, false);
  assert.equal(validateCheckUrl('not-a-url').ok, false);
});

test('classifyContent detects broken and blocked markers', () => {
  assert.equal(classifyContent('Product not found on this page'), 'BROKEN');
  assert.equal(classifyContent('Produkt jest niedostępny'), 'BROKEN');
  assert.equal(classifyContent('Please complete the captcha'), 'BLOCKED');
  assert.equal(classifyContent('Healthy product page'), null);
});

test('SingleFlightQueue runs one task at a time', async () => {
  const queue = new SingleFlightQueue();
  let concurrent = 0;
  let maxConcurrent = 0;

  const task = async () => {
    concurrent += 1;
    maxConcurrent = Math.max(maxConcurrent, concurrent);
    await new Promise((resolve) => setTimeout(resolve, 20));
    concurrent -= 1;
  };

  await Promise.all([queue.enqueue(task), queue.enqueue(task)]);
  assert.equal(maxConcurrent, 1);
});
