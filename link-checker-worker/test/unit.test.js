import test from 'node:test';
import assert from 'node:assert/strict';
import {
  analyzeFinalUrl,
  classifyContent,
  createLinkChecker,
  detectAliExpressRegionalUnavailable,
  detectAliExpressSoft404,
  evaluatePage,
  hasAliExpressMainProductSignal,
  hasPositiveProductSignal,
  waitForPageStability,
} from '../src/check-link.js';
import { DomainCooldown } from '../src/domain-cooldown.js';
import { SingleFlightQueue } from '../src/queue.js';

test('classifyContent detects broken and blocked markers', () => {
  assert.equal(classifyContent('Product not found on this page'), 'BROKEN');
  assert.equal(classifyContent('Produkt nie istnieje'), 'BROKEN');
  assert.equal(classifyContent('Please complete the captcha'), 'BLOCKED');
  assert.equal(classifyContent('Healthy product page'), null);
});

const productPage = {
  initialUrl: 'https://shop.example/product/123',
  finalUrl: 'https://shop.example/product/123',
  bodyText: 'Example product',
  signals: { hasProductJsonLd: true },
};

const aliExpressPage = {
  initialUrl: 'https://pl.aliexpress.com/item/1005010538111290.html',
  finalUrl: 'https://pl.aliexpress.com/item/1005010538111290.html',
  httpStatus: 200,
  bodyText: 'AliExpress product page',
  signals: {},
};

const regionalAliExpressPage = {
  ...aliExpressPage,
  initialUrl: 'https://pl.aliexpress.com/item/1005005477240128.html',
  finalUrl: 'https://pl.aliexpress.com/item/1005005477240128.html',
};

test('AliExpress regional unavailability is broken with a dedicated reason', () => {
  const bodyText = 'Przepraszamy, ten przedmiot jest obecnie niedostępny w Twoim kraju.';
  const classification = evaluatePage({ ...regionalAliExpressPage, bodyText });

  assert.equal(detectAliExpressRegionalUnavailable(bodyText), true);
  assert.equal(classification.status, 'BROKEN');
  assert.equal(classification.reason, 'PRODUCT_UNAVAILABLE_IN_REGION');
});

test('AliExpress recommendations cannot override regional unavailability', () => {
  const classification = evaluatePage({
    ...regionalAliExpressPage,
    bodyText:
      'Ten przedmiot jest obecnie niedostępny w Twoim kraju. Te oferty też mogą Ci się spodobać. Kup teraz 19,99 zł.',
    signals: {
      hasMatchingProductJsonLd: true,
      hasAliExpressMainProductContainer: true,
      hasMainPriceOrAvailability: true,
      hasMainPurchaseAction: true,
    },
  });

  assert.equal(classification.status, 'BROKEN');
  assert.equal(classification.reason, 'PRODUCT_UNAVAILABLE_IN_REGION');
});

test('AliExpress without regional message or main product confirmation remains uncertain', () => {
  const classification = evaluatePage({ ...regionalAliExpressPage, bodyText: 'AliExpress recommendations' });
  assert.equal(classification.status, 'UNCERTAIN');
  assert.equal(classification.reason, 'PRODUCT_NOT_CONFIRMED');
});

test('AliExpress CAPTCHA takes precedence over regional unavailability', () => {
  const classification = evaluatePage({
    ...regionalAliExpressPage,
    bodyText: 'Captcha. Ten przedmiot jest obecnie niedostępny w Twoim kraju.',
  });
  assert.equal(classification.status, 'BLOCKED');
  assert.equal(classification.reason, 'CAPTCHA_DETECTED');
});

test('confirmed AliExpress product remains working without regional message', () => {
  const classification = evaluatePage({
    ...regionalAliExpressPage,
    bodyText: 'AliExpress product page',
    signals: { hasMatchingProductJsonLd: true },
  });
  assert.equal(classification.status, 'WORKING');
  assert.equal(classification.reason, 'PRODUCT_CONFIRMED');
});

test('AliExpress HTTP 200 soft 404 is broken with PRODUCT_NOT_FOUND', () => {
  const bodyText = 'Przepraszamy, nie znaleziono pożądanej strony';
  const classification = evaluatePage({ ...aliExpressPage, bodyText });

  assert.equal(detectAliExpressSoft404(bodyText), true);
  assert.equal(classification.status, 'BROKEN');
  assert.equal(classification.reason, 'PRODUCT_NOT_FOUND');
});

test('AliExpress recommendations cannot confirm the missing main product', () => {
  const classification = evaluatePage({
    ...aliExpressPage,
    bodyText: 'Te oferty też mogą Ci się spodobać. Produkt A 19,99 zł. Kup teraz.',
    signals: {
      hasProductTitle: true,
      hasPriceOrAvailability: true,
      hasPurchaseAction: true,
      hasProductJsonLd: true,
    },
  });

  assert.equal(classification.status, 'UNCERTAIN');
  assert.equal(classification.reason, 'PRODUCT_NOT_CONFIRMED');
});

test('AliExpress main product evidence confirms a working product', () => {
  const signals = { hasMatchingProductJsonLd: true };
  const classification = evaluatePage({ ...aliExpressPage, signals });

  assert.equal(hasAliExpressMainProductSignal(signals), true);
  assert.equal(classification.status, 'WORKING');
  assert.equal(classification.reason, 'PRODUCT_CONFIRMED');
});

test('AliExpress without an error or main product evidence remains uncertain', () => {
  const classification = evaluatePage({ ...aliExpressPage });
  assert.equal(classification.status, 'UNCERTAIN');
  assert.equal(classification.reason, 'PRODUCT_NOT_CONFIRMED');
});

test('AliExpress CAPTCHA takes precedence over soft 404', () => {
  const classification = evaluatePage({
    ...aliExpressPage,
    bodyText: 'Captcha. Przepraszamy, nie znaleziono pożądanej strony',
    signals: { hasMatchingProductJsonLd: true },
  });
  assert.equal(classification.status, 'BLOCKED');
  assert.equal(classification.reason, 'CAPTCHA_DETECTED');
});

test('page stabilization observes a delayed AliExpress soft 404', async () => {
  let currentTime = 0;
  let reads = 0;
  const page = {
    locator: () => ({
      innerText: async () => {
        reads += 1;
        return reads === 1
          ? 'Ładowanie strony produktu'
          : 'Przepraszamy, nie znaleziono pożądanej strony';
      },
    }),
    evaluate: async () => ({
      hasProductTitle: true,
      hasPriceOrAvailability: true,
      hasPurchaseAction: true,
    }),
    waitForTimeout: async (milliseconds) => {
      currentTime += milliseconds;
    },
  };

  const snapshot = await waitForPageStability(page, aliExpressPage.finalUrl, {
    settleMs: 2000,
    pollMs: 200,
    now: () => currentTime,
  });
  const classification = evaluatePage({ ...aliExpressPage, ...snapshot });

  assert.equal(reads, 2);
  assert.equal(classification.status, 'BROKEN');
  assert.equal(classification.reason, 'PRODUCT_NOT_FOUND');
});

test('HTTP blocks and server errors are classified conservatively', () => {
  assert.equal(evaluatePage({ ...productPage, httpStatus: 403 }).status, 'BLOCKED');
  assert.equal(evaluatePage({ ...productPage, httpStatus: 429 }).status, 'BLOCKED');
  assert.equal(evaluatePage({ ...productPage, httpStatus: 500 }).status, 'UNCERTAIN');
  assert.equal(evaluatePage({ ...productPage, httpStatus: 503 }).status, 'UNCERTAIN');
  assert.equal(evaluatePage({ ...productPage, httpStatus: 403, bodyText: 'Captcha challenge' }).reason, 'CAPTCHA_DETECTED');
});

test('navigation timeout is uncertain and has a safe reason', async () => {
  const fakePage = {
    goto: async () => {
      throw new Error('Navigation timeout of 30000 ms exceeded');
    },
    url: () => productPage.initialUrl,
  };
  const checker = createLinkChecker(async (callback) => callback(fakePage), new DomainCooldown(600000));

  const classification = await checker(productPage.initialUrl);

  assert.equal(classification.status, 'UNCERTAIN');
  assert.equal(classification.reason, 'NAVIGATION_TIMEOUT');
});

test('plain 404 text is not a broken marker but a real HTTP 404 is broken', () => {
  assert.equal(classifyContent('Order number 404 is ready'), null);
  assert.equal(evaluatePage({ ...productPage, httpStatus: 404 }).status, 'BROKEN');
});

test('redirects away from a product are uncertain', () => {
  for (const finalUrl of [
    'https://shop.example/',
    'https://shop.example/category/kitchen',
    'https://shop.example/login',
  ]) {
    assert.equal(evaluatePage({ ...productPage, finalUrl, httpStatus: 200 }).reason, 'REDIRECTED_AWAY_FROM_PRODUCT');
  }
});

test('canonical product redirect can remain working', () => {
  const classification = evaluatePage({
    ...productPage,
    finalUrl: 'https://www.shop.example/product/example-name-123',
    httpStatus: 200,
  });
  assert.equal(classification.status, 'WORKING');
});

test('positive product evidence is required', () => {
  assert.equal(hasPositiveProductSignal({ hasProductJsonLd: true }), true);
  assert.equal(evaluatePage({ ...productPage, httpStatus: 200, signals: {} }).status, 'UNCERTAIN');
  assert.equal(evaluatePage({ ...productPage, httpStatus: 200, signals: {} }).reason, 'PRODUCT_NOT_CONFIRMED');
});

test('a generic h1 or page title alone does not confirm a product', () => {
  assert.equal(
    evaluatePage({ ...productPage, httpStatus: 200, signals: { hasMainProductTitle: true } }).status,
    'UNCERTAIN',
  );
  assert.equal(
    evaluatePage({ ...productPage, httpStatus: 200, signals: { hasPageTitle: true } }).reason,
    'PRODUCT_NOT_CONFIRMED',
  );
});

test('two independent main product signals confirm a product', () => {
  const classification = evaluatePage({
    ...productPage,
    httpStatus: 200,
    signals: { hasMainProductTitle: true, hasMainPriceOrAvailability: true },
  });
  assert.equal(classification.status, 'WORKING');
  assert.equal(classification.reason, 'PRODUCT_CONFIRMED');
});

test('recommendation signals outside the main product do not confirm it', () => {
  const classification = evaluatePage({
    ...productPage,
    httpStatus: 200,
    signals: {
      hasMainProductTitle: true,
      hasPriceOrAvailability: true,
      hasPurchaseAction: true,
    },
  });
  assert.equal(classification.status, 'UNCERTAIN');
  assert.equal(classification.reason, 'PRODUCT_NOT_CONFIRMED');
});

test('explicit removed offer message is broken', () => {
  const classification = evaluatePage({ ...productPage, httpStatus: 200, bodyText: 'Ta oferta jest zakończona' });
  assert.equal(classification.status, 'BROKEN');
  assert.equal(classification.reason, 'OFFER_ENDED');
});

test('domain cooldown blocks until it expires', () => {
  let now = 1000;
  const cooldown = new DomainCooldown(600000, () => now);
  const url = 'https://shop.example/product/123';

  assert.equal(cooldown.isBlocked(url), false);
  cooldown.block(url);
  assert.equal(cooldown.isBlocked(url), true);
  now += 600001;
  assert.equal(cooldown.isBlocked(url), false);
});

test('captcha starts cooldown and the next request does not open a page', async () => {
  let now = 1000;
  let pageRuns = 0;
  const cooldown = new DomainCooldown(600000, () => now);
  const fakePage = {
    goto: async () => ({ status: () => 403 }),
    url: () => productPage.finalUrl,
    locator: () => ({ innerText: async () => 'Please complete the captcha' }),
    evaluate: async () => ({}),
  };
  const checker = createLinkChecker(async (callback) => {
    pageRuns += 1;
    return callback(fakePage);
  }, cooldown);

  const first = await checker(productPage.initialUrl);
  const second = await checker(productPage.initialUrl);

  assert.equal(first.reason, 'CAPTCHA_DETECTED');
  assert.equal(second.reason, 'DOMAIN_COOLDOWN');
  assert.equal(pageRuns, 1);

  now += 600001;
  await checker(productPage.initialUrl);
  assert.equal(pageRuns, 2);
});

test('URL analysis keeps product paths and rejects foreign domains', () => {
  assert.equal(analyzeFinalUrl(productPage.initialUrl, productPage.finalUrl).productLike, true);
  assert.equal(analyzeFinalUrl(productPage.initialUrl, 'https://other.example/product/123').redirectedAway, true);
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
