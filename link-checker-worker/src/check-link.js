import { config } from './config.js';
import { withPage } from './browser-manager.js';
import { DomainCooldown } from './domain-cooldown.js';
import { shopGroup, validateMainNavigation, UrlValidationError } from './url-validator.js';
import { CheckTimeoutError } from './total-timeout.js';

const BLOCKED_MARKERS = [
  'captcha',
  'verify you are human',
  'access denied',
  'unusual traffic',
  'too many requests',
  'robot check',
  'security check',
  'temporarily blocked',
  'potwierdź, że jesteś człowiekiem',
  'nietypowy ruch',
  'zbyt wiele żądań',
  'kontrola bezpieczeństwa',
  'tymczasowo zablokowano',
  'odmowa dostępu',
];

const BROKEN_MARKERS = [
  'product not found',
  'item is no longer available',
  'this item has been removed',
  'this offer has ended',
  'produkt nie istnieje',
  'produkt został usunięty',
  'oferta została usunięta',
  'oferta zakończona',
  'ta oferta jest zakończona',
  'nie możemy znaleźć tego produktu',
];

const ALIEXPRESS_SOFT_404_MARKERS = [
  'przepraszamy, nie znaleziono pożądanej strony',
  'przepraszamy, nie znaleziono żądanej strony',
  'sorry, the page you requested can not be found',
  'sorry, the page you requested cannot be found',
  'the item you requested is not available',
];

const ALIEXPRESS_REGIONAL_UNAVAILABLE_MARKERS = [
  'ten przedmiot jest obecnie niedostępny w twoim kraju',
  'produkt jest niedostępny w twoim kraju',
  'this item is currently unavailable in your country',
  'this item is currently unavailable in your region',
  'this product is unavailable in your country',
  'this product is unavailable in your region',
];

const NON_PRODUCT_PATH_MARKERS = [
  '/login',
  '/signin',
  '/sign-in',
  '/search',
  '/category',
  '/categories',
  '/catalog',
  '/error',
];

const PRODUCT_PATH_MARKERS = [
  '/product/',
  '/products/',
  '/item/',
  '/offer/',
  '/oferta/',
  '/dp/',
  '/gp/product/',
];

const domainCooldown = new DomainCooldown(config.domainBlockCooldownMs);

function normalizedText(text) {
  return (text ?? '').toLowerCase().replace(/\s+/g, ' ').trim();
}

export function detectBlock(text) {
  const content = normalizedText(text);
  return BLOCKED_MARKERS.some((marker) => content.includes(marker));
}

export function detectRemoval(text) {
  const content = normalizedText(text);
  return BROKEN_MARKERS.some((marker) => content.includes(marker));
}

export function isAliExpressItemUrl(url) {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase().replace(/\.+$/, '');
    return (host === 'aliexpress.com' || host.endsWith('.aliexpress.com')) && /\/item\/\d+\.html(?:\/)?$/i.test(parsed.pathname);
  } catch {
    return false;
  }
}

function aliExpressItemId(url) {
  if (!isAliExpressItemUrl(url)) {
    return null;
  }
  return new URL(url).pathname.match(/\/item\/(\d+)\.html/i)?.[1] ?? null;
}

export function detectAliExpressSoft404(text) {
  const content = normalizedText(text);
  return ALIEXPRESS_SOFT_404_MARKERS.some((marker) => content.includes(marker));
}

export function detectAliExpressRegionalUnavailable(text) {
  const content = normalizedText(text);
  return ALIEXPRESS_REGIONAL_UNAVAILABLE_MARKERS.some((marker) => content.includes(marker));
}

function hostnamesAreRelated(initialHostname, finalHostname) {
  return (
    initialHostname === finalHostname ||
    initialHostname.endsWith(`.${finalHostname}`) ||
    finalHostname.endsWith(`.${initialHostname}`) ||
    shopGroup(initialHostname) !== null && shopGroup(initialHostname) === shopGroup(finalHostname)
  );
}

function pathLooksLikeProduct(pathname) {
  const normalizedPath = pathname.toLowerCase();
  return PRODUCT_PATH_MARKERS.some((marker) => normalizedPath.includes(marker));
}

export function analyzeFinalUrl(initialUrl, finalUrl) {
  let initial;
  let final;
  try {
    initial = new URL(initialUrl);
    final = new URL(finalUrl);
  } catch {
    return { redirectedAway: true, productLike: false };
  }

  const path = final.pathname.toLowerCase().replace(/\/+$/, '') || '/';
  const redirectedAway =
    !hostnamesAreRelated(initial.hostname.toLowerCase(), final.hostname.toLowerCase()) ||
    path === '/' ||
    NON_PRODUCT_PATH_MARKERS.some((marker) => path === marker || path.startsWith(`${marker}/`));

  return {
    redirectedAway,
    productLike: !redirectedAway && (pathLooksLikeProduct(path) || pathLooksLikeProduct(initial.pathname)),
  };
}

export function hasPositiveProductSignal(signals = {}) {
  if (signals.hasProductJsonLd || signals.hasMainProductContainer || signals.hasMatchingProductId) {
    return true;
  }
  const weakSignals = [
    signals.hasMainProductTitle,
    signals.hasMainPriceOrAvailability,
    signals.hasMainPurchaseAction,
    signals.hasProductMetadata,
  ];
  return weakSignals.filter(Boolean).length >= 2;
}

export function hasAliExpressMainProductSignal(signals = {}) {
  return Boolean(signals.hasMatchingProductJsonLd || signals.hasAliExpressMainProductContainer);
}

function hasConfirmedProduct(url, signals) {
  return isAliExpressItemUrl(url)
    ? hasAliExpressMainProductSignal(signals)
    : hasPositiveProductSignal(signals);
}

export function evaluatePage({ initialUrl, finalUrl, httpStatus, bodyText, signals }) {
  if (detectBlock(bodyText)) {
    return { status: 'BLOCKED', reason: 'CAPTCHA_DETECTED', startsCooldown: true };
  }

  if (httpStatus === 403) {
    return { status: 'BLOCKED', reason: 'ACCESS_BLOCKED', startsCooldown: true };
  }
  if (httpStatus === 429) {
    return { status: 'BLOCKED', reason: 'RATE_LIMITED', startsCooldown: true };
  }

  const productPageUrl = isAliExpressItemUrl(finalUrl) ? finalUrl : initialUrl;
  if (isAliExpressItemUrl(productPageUrl) && detectAliExpressRegionalUnavailable(bodyText)) {
    return { status: 'BROKEN', reason: 'PRODUCT_UNAVAILABLE_IN_REGION', startsCooldown: false };
  }

  if ([500, 502, 503, 504].includes(httpStatus)) {
    return { status: 'UNCERTAIN', reason: 'SERVER_ERROR', startsCooldown: false };
  }

  if (isAliExpressItemUrl(productPageUrl) && detectAliExpressSoft404(bodyText)) {
    return { status: 'BROKEN', reason: 'PRODUCT_NOT_FOUND', startsCooldown: false };
  }

  const finalUrlAssessment = analyzeFinalUrl(initialUrl, finalUrl);
  if (finalUrlAssessment.redirectedAway) {
    return { status: 'UNCERTAIN', reason: 'REDIRECTED_AWAY_FROM_PRODUCT', startsCooldown: false };
  }

  if (httpStatus === 404 || httpStatus === 410) {
    return { status: 'BROKEN', reason: 'PRODUCT_NOT_FOUND', startsCooldown: false };
  }
  if (httpStatus !== null && httpStatus >= 400) {
    return { status: 'UNCERTAIN', reason: 'HTTP_ERROR', startsCooldown: false };
  }

  if (detectRemoval(bodyText)) {
    return { status: 'BROKEN', reason: 'OFFER_ENDED', startsCooldown: false };
  }

  if (!finalUrlAssessment.productLike || !hasConfirmedProduct(productPageUrl, signals)) {
    return { status: 'UNCERTAIN', reason: 'PRODUCT_NOT_CONFIRMED', startsCooldown: false };
  }

  return { status: 'WORKING', reason: 'PRODUCT_CONFIRMED', startsCooldown: false };
}

async function collectProductSignals(page, pageUrl) {
  const mainProductId = aliExpressItemId(pageUrl);
  return page.evaluate((expectedProductId) => {
    const jsonLdScripts = Array.from(document.querySelectorAll('script[type="application/ld+json"]'));
    const hasProductJsonLd = jsonLdScripts.some((script) => /["']@type["']\s*:\s*["']Product["']/i.test(script.textContent ?? ''));
    const hasMatchingProductJsonLd = jsonLdScripts.some((script) => {
      const content = script.textContent ?? '';
      return /["']@type["']\s*:\s*["']Product["']/i.test(content) && Boolean(expectedProductId && content.includes(expectedProductId));
    });
    const productMetadata = document.querySelector(
      'meta[property="og:type"][content*="product" i], meta[property="product:price:amount"], [itemtype*="schema.org/Product" i]',
    );
    const mainProductTitle = document.querySelector(
      'h1, [data-testid*="product-title" i], [class*="product-title" i], [itemprop="name"]',
    );
    const mainProductContainer = mainProductTitle?.closest(
      '[itemtype*="schema.org/Product" i], #product-detail, [data-testid*="product" i], [class*="product--main" i], [class*="product-detail" i]',
    );
    const mainPriceOrAvailability = mainProductContainer?.querySelector(
      '[itemprop="price"], [itemprop="availability"], [data-testid*="price" i], [class*="price" i]',
    );
    const mainPurchaseAction = Array.from(
      mainProductContainer?.querySelectorAll('button, [role="button"], input[type="submit"]') ?? [],
    ).some((element) =>
      /add to cart|buy now|dodaj do koszyka|kup teraz/i.test(element.textContent ?? element.getAttribute('value') ?? ''),
    );
    const aliExpressMainContainer = document.querySelector(
      '[data-pl="product-main"], #product-detail, .product-main, [class*="product--main"], [class*="product-detail"]',
    );
    const aliExpressMainTitle = aliExpressMainContainer?.querySelector(
      'h1, [data-pl="product-title"], [class*="product--title"], [itemprop="name"]',
    );

    return {
      hasProductJsonLd,
      hasMatchingProductJsonLd,
      hasProductMetadata: Boolean(productMetadata),
      hasMainProductContainer: Boolean(
        mainProductContainer &&
          (mainProductContainer.matches('[itemtype*="schema.org/Product" i]') ||
            (mainPriceOrAvailability || mainPurchaseAction)),
      ),
      hasMainProductTitle: Boolean(mainProductTitle && (mainProductTitle.textContent ?? '').trim()),
      hasMainPriceOrAvailability: Boolean(mainPriceOrAvailability),
      hasMainPurchaseAction: mainPurchaseAction,
      hasAliExpressMainProductContainer: Boolean(
        aliExpressMainContainer && aliExpressMainTitle && (aliExpressMainTitle.textContent ?? '').trim(),
      ),
    };
  }, mainProductId);
}

async function readPageSnapshot(page, pageUrl, bodyTimeoutMs) {
  const [bodyText, signals] = await Promise.all([
    page.locator('body').innerText({ timeout: bodyTimeoutMs }).catch(() => ''),
    collectProductSignals(page, pageUrl).catch(() => ({})),
  ]);
  return { bodyText, signals };
}

function snapshotIsDecisive(pageUrl, snapshot) {
  if (detectBlock(snapshot.bodyText)) {
    return true;
  }
  if (isAliExpressItemUrl(pageUrl)) {
    return (
      detectAliExpressRegionalUnavailable(snapshot.bodyText) ||
      detectAliExpressSoft404(snapshot.bodyText)
    );
  }
  return hasConfirmedProduct(pageUrl, snapshot.signals);
}

export async function waitForPageStability(
  page,
  pageUrl,
  { settleMs = config.pageSettleMs, pollMs = 200, now = () => Date.now() } = {},
) {
  const deadline = now() + settleMs;
  let snapshot = await readPageSnapshot(page, pageUrl, Math.max(1, deadline - now()));

  while (!snapshotIsDecisive(pageUrl, snapshot) && now() < deadline) {
    await page.waitForTimeout(Math.min(pollMs, Math.max(1, deadline - now())));
    snapshot = await readPageSnapshot(page, pageUrl, Math.max(1, deadline - now()));
  }
  return snapshot;
}

function result(status, reason, finalUrl, httpStatus) {
  return { status, reason, finalUrl, httpStatus, checkedAt: new Date().toISOString() };
}

export function createLinkChecker(
  pageRunner = withPage,
  cooldown = domainCooldown,
  navigationValidator = validateMainNavigation,
  stabilizePage = waitForPageStability,
) {
  return async (url) => {
    if (cooldown.isBlocked(url)) {
      return result('BLOCKED', 'DOMAIN_COOLDOWN', url, null);
    }

    try {
      return await pageRunner(async (page) => {
        let redirectError;
        const pendingNavigationValidations = new Set();
        if (typeof page.route === 'function') {
          await page.route('**/*', async (route) => {
            const request = route.request();
            const mainNavigation = request.isNavigationRequest() && request.frame() === page.mainFrame();
            if (!mainNavigation) {
              await route.continue();
              return;
            }
            const validation = (async () => {
              try {
                await navigationValidator(request.url(), url);
                await route.continue();
              } catch {
                redirectError = new UrlValidationError(403, 'REDIRECT_BLOCKED');
                await route.abort('blockedbyclient');
              }
            })();
            pendingNavigationValidations.add(validation);
            try {
              await validation;
            } finally {
              pendingNavigationValidations.delete(validation);
            }
          });
        }

        let response;
        try {
          response = await page.goto(url, {
            waitUntil: 'domcontentloaded',
            timeout: config.checkTimeoutMs,
          });
        } catch (error) {
          if (redirectError) {
            throw redirectError;
          }
          const message = error instanceof Error ? error.message : String(error);
          if (message.toLowerCase().includes('timeout')) {
            return result('UNCERTAIN', 'NAVIGATION_TIMEOUT', page.url(), null);
          }
          throw error;
        }

        const httpStatus = response?.status() ?? null;
        const urlAfterGoto = page.url();
        const { bodyText, signals } = await stabilizePage(page, urlAfterGoto);
        await Promise.resolve();
        while (pendingNavigationValidations.size > 0) {
          await Promise.allSettled([...pendingNavigationValidations]);
        }
        if (redirectError) {
          throw redirectError;
        }
        const finalUrl = page.url();
        const classification = evaluatePage({ initialUrl: url, finalUrl, httpStatus, bodyText, signals });

        if (classification.startsCooldown) {
          cooldown.block(url);
        }

        return result(classification.status, classification.reason, finalUrl, httpStatus);
      });
    } catch (error) {
      if (error instanceof CheckTimeoutError) {
        return result('UNCERTAIN', 'CHECK_TIMEOUT', url, null);
      }
      throw error;
    }
  };
}

export const checkLink = createLinkChecker();

export function getDomainCooldownResult(url) {
  return domainCooldown.isBlocked(url) ? result('BLOCKED', 'DOMAIN_COOLDOWN', url, null) : null;
}

export function classifyContent(text) {
  if (detectBlock(text)) {
    return 'BLOCKED';
  }
  if (detectRemoval(text)) {
    return 'BROKEN';
  }
  return null;
}
