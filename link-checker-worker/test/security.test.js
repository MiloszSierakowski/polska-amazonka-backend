import test from 'node:test';
import assert from 'node:assert/strict';
import { createLinkChecker } from '../src/check-link.js';
import { DomainCooldown } from '../src/domain-cooldown.js';
import { QueueFullError, SingleFlightQueue } from '../src/queue.js';
import { readJsonBody, RequestBodyTooLargeError } from '../src/request-body.js';
import { CheckTimeoutError, withTotalTimeout } from '../src/total-timeout.js';
import { createNavigationValidator, createUrlValidator, UrlValidationError } from '../src/url-validator.js';

const allowedHosts = ['allegro.pl', 'amazon.pl', 'aliexpress.com', 'temu.com', 'amzn.to', 'temu.to'];
const publicDns = async () => [{ address: '93.184.216.34', family: 4 }];

function validator(resolve = publicDns) {
  return createUrlValidator({ allowedHosts, resolve });
}

async function rejectedReason(promise, reason) {
  await assert.rejects(promise, (error) => error instanceof UrlValidationError && error.reason === reason);
}

test('supported shop domains and their real subdomains are accepted', async () => {
  assert.equal(await validator()('https://allegro.pl/oferta/123'), 'https://allegro.pl/oferta/123');
  assert.equal(await validator()('https://WWW.AMAZON.PL./dp/ABC'), 'https://www.amazon.pl/dp/ABC');
});

test('lookalike and unsupported domains are rejected', async () => {
  await rejectedReason(validator()('https://allegro.pl.example.com/product/1'), 'UNSUPPORTED_HOST');
  await rejectedReason(validator()('https://example.com/product/1'), 'UNSUPPORTED_HOST');
});

test('credentials and local hostnames are rejected', async () => {
  await rejectedReason(validator()('https://user:secret@allegro.pl/oferta/1'), 'INVALID_URL');
  await rejectedReason(validator()('http://localhost/product/1'), 'PRIVATE_ADDRESS_BLOCKED');
});

test('direct local, private, metadata and IPv6 addresses are rejected', async () => {
  for (const url of [
    'http://127.0.0.1/',
    'http://10.10.0.1/',
    'http://169.254.169.254/latest/meta-data/',
    'http://[::1]/',
    'http://[fc00::1]/',
  ]) {
    await rejectedReason(validator()(url), 'PRIVATE_ADDRESS_BLOCKED');
  }
});

test('a hostname resolving to any private address is rejected', async () => {
  const mixedDns = async () => [
    { address: '93.184.216.34', family: 4 },
    { address: '192.168.1.10', family: 4 },
  ];
  await rejectedReason(validator(mixedDns)('https://allegro.pl/oferta/1'), 'PRIVATE_ADDRESS_BLOCKED');
});

test('DNS failures use a safe reason', async () => {
  const failedDns = async () => {
    throw new Error('internal resolver details');
  };
  await rejectedReason(validator(failedDns)('https://allegro.pl/oferta/1'), 'DNS_RESOLUTION_FAILED');
});

test('redirect validation blocks private targets and allows canonical shop targets', async () => {
  const navigationValidator = createNavigationValidator(validator());
  await rejectedReason(
    navigationValidator('http://127.0.0.1/internal', 'https://allegro.pl/oferta/1'),
    'PRIVATE_ADDRESS_BLOCKED',
  );
  assert.equal(
    await navigationValidator('https://www.allegro.pl/oferta/canonical-123', 'https://allegro.pl/oferta/1'),
    'https://www.allegro.pl/oferta/canonical-123',
  );
  await rejectedReason(
    navigationValidator('https://amazon.pl/dp/ABC', 'https://allegro.pl/oferta/1'),
    'REDIRECT_BLOCKED',
  );
});

test('main navigation rejection stops the check before page analysis', async () => {
  let routeHandler;
  let analysisStarted = false;
  const page = {
    route: async (_pattern, handler) => {
      routeHandler = handler;
    },
    mainFrame: () => 'main-frame',
    goto: async () => {
      const request = {
        isNavigationRequest: () => true,
        frame: () => 'main-frame',
        url: () => 'http://127.0.0.1/internal',
      };
      const route = {
        request: () => request,
        continue: async () => {},
        abort: async () => {},
      };
      await routeHandler(route);
      throw new Error('navigation aborted');
    },
    url: () => 'https://allegro.pl/oferta/1',
    locator: () => {
      analysisStarted = true;
      return { innerText: async () => '' };
    },
  };
  const checker = createLinkChecker(
    async (callback) => callback(page),
    new DomainCooldown(600000),
    validator(),
  );

  await rejectedReason(checker('https://allegro.pl/oferta/1'), 'REDIRECT_BLOCKED');
  assert.equal(analysisStarted, false);
});

function navigationTestPage(initialUrl) {
  let currentUrl = initialUrl;
  let routeHandler;
  return {
    page: {
      route: async (_pattern, handler) => {
        routeHandler = handler;
      },
      mainFrame: () => 'main-frame',
      goto: async () => ({ status: () => 200 }),
      url: () => currentUrl,
    },
    navigate: async (targetUrl) => {
      const request = {
        isNavigationRequest: () => true,
        frame: () => 'main-frame',
        url: () => targetUrl,
      };
      await routeHandler({
        request: () => request,
        continue: async () => {
          currentUrl = targetUrl;
        },
        abort: async () => {},
      });
    },
    setUrl: (url) => {
      currentUrl = url;
    },
  };
}

test('finalUrl is read again after JavaScript redirects to the shop home page', async () => {
  const originalUrl = 'https://allegro.pl/oferta/product-123';
  const fake = navigationTestPage(originalUrl);
  const checker = createLinkChecker(
    async (callback) => callback(fake.page),
    new DomainCooldown(600000),
    async () => originalUrl,
    async () => {
      fake.setUrl('https://allegro.pl/');
      return { bodyText: 'Shop home', signals: { hasProductJsonLd: true } };
    },
  );

  const result = await checker(originalUrl);
  assert.equal(result.status, 'UNCERTAIN');
  assert.equal(result.reason, 'REDIRECTED_AWAY_FROM_PRODUCT');
  assert.equal(result.finalUrl, 'https://allegro.pl/');
});

for (const [label, targetUrl] of [
  ['private address', 'http://127.0.0.1/internal'],
  ['unsupported domain', 'https://evil.example/product/1'],
]) {
  test(`late JavaScript redirect to ${label} returns REDIRECT_BLOCKED`, async () => {
    const originalUrl = 'https://allegro.pl/oferta/product-123';
    const fake = navigationTestPage(originalUrl);
    const navigationValidator = async (candidateUrl) => {
      if (candidateUrl !== originalUrl) {
        throw new UrlValidationError(403, 'REDIRECT_BLOCKED');
      }
      return candidateUrl;
    };
    const checker = createLinkChecker(
      async (callback) => callback(fake.page),
      new DomainCooldown(600000),
      navigationValidator,
      async () => {
        await fake.navigate(targetUrl);
        return { bodyText: 'Old product page', signals: { hasProductJsonLd: true } };
      },
    );

    await rejectedReason(checker(originalUrl), 'REDIRECT_BLOCKED');
  });
}

test('late canonical navigation within the same shop remains allowed', async () => {
  const originalUrl = 'https://allegro.pl/oferta/product-123';
  const canonicalUrl = 'https://www.allegro.pl/oferta/product-name-123';
  const fake = navigationTestPage(originalUrl);
  const checker = createLinkChecker(
    async (callback) => callback(fake.page),
    new DomainCooldown(600000),
    async (candidateUrl) => candidateUrl,
    async () => {
      await fake.navigate(canonicalUrl);
      return { bodyText: 'Product page', signals: { hasProductJsonLd: true } };
    },
  );

  const result = await checker(originalUrl);
  assert.equal(result.status, 'WORKING');
  assert.equal(result.finalUrl, canonicalUrl);
});

test('oversized request body is rejected while reading', async () => {
  let resumed = false;
  const request = {
    async *[Symbol.asyncIterator]() {
      yield Buffer.alloc(10);
      yield Buffer.alloc(10);
    },
    resume: () => {
      resumed = true;
    },
  };

  await assert.rejects(readJsonBody(request, 16), RequestBodyTooLargeError);
  assert.equal(resumed, true);
});

test('full queue rejects a new task and accepts work after space is released', async () => {
  const queue = new SingleFlightQueue(1);
  let releaseFirst;
  const first = queue.enqueue(() => new Promise((resolve) => {
    releaseFirst = resolve;
  }));
  const second = queue.enqueue(async () => 'second');

  assert.equal(queue.getPendingCount(), 1);
  assert.equal(queue.isFull(), true);
  await assert.rejects(queue.enqueue(async () => 'third'), QueueFullError);
  releaseFirst('first');
  await first;
  await second;
  assert.equal(await queue.enqueue(async () => 'next'), 'next');
});

test('total timeout cleans resources and the checker returns UNCERTAIN', async () => {
  let cleaned = false;
  await assert.rejects(
    withTotalTimeout(() => new Promise(() => {}), 1, async () => {
      cleaned = true;
    }),
    CheckTimeoutError,
  );
  assert.equal(cleaned, true);

  const checker = createLinkChecker(
    async () => {
      throw new CheckTimeoutError();
    },
    new DomainCooldown(600000),
  );
  const result = await checker('https://allegro.pl/oferta/1');
  assert.equal(result.status, 'UNCERTAIN');
  assert.equal(result.reason, 'CHECK_TIMEOUT');
});

test('resource cleanup also runs after an operation exception', async () => {
  let cleaned = false;
  await assert.rejects(
    withTotalTimeout(async () => {
      throw new Error('operation failed');
    }, 1000, async () => {
      cleaned = true;
    }),
  );
  assert.equal(cleaned, true);
});
