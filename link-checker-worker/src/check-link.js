import { config } from './config.js';
import { withPage } from './browser-manager.js';

const BROKEN_MARKERS = [
  'page not found',
  '404',
  'product not found',
  'item is unavailable',
  'nie możemy znaleźć tej strony',
  'przedmiot chwilowo niedostępny',
  'produkt jest niedostępny',
];

const BLOCKED_MARKERS = [
  'access denied',
  'captcha',
  'verify you are human',
  'robot check',
  'security check',
  'blocked',
];

function classifyContent(text) {
  const normalized = (text ?? '').toLowerCase();
  for (const marker of BLOCKED_MARKERS) {
    if (normalized.includes(marker)) {
      return 'BLOCKED';
    }
  }
  for (const marker of BROKEN_MARKERS) {
    if (normalized.includes(marker)) {
      return 'BROKEN';
    }
  }
  return null;
}

export async function checkLink(url) {
  return withPage(async (page) => {
    let response;
    try {
      response = await page.goto(url, {
        waitUntil: 'domcontentloaded',
        timeout: config.checkTimeoutMs,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (message.toLowerCase().includes('timeout')) {
        return {
          status: 'UNCERTAIN',
          reason: 'Navigation timeout',
          finalUrl: page.url(),
          httpStatus: null,
          checkedAt: new Date().toISOString(),
        };
      }
      throw error;
    }

    const httpStatus = response?.status() ?? null;
    const finalUrl = page.url();
    const bodyText = await page.locator('body').innerText({ timeout: 5000 }).catch(() => '');

    if (httpStatus !== null && httpStatus >= 400) {
      return {
        status: 'BROKEN',
        reason: `HTTP ${httpStatus}`,
        finalUrl,
        httpStatus,
        checkedAt: new Date().toISOString(),
      };
    }

    const contentStatus = classifyContent(bodyText);
    if (contentStatus === 'BLOCKED') {
      return {
        status: 'BLOCKED',
        reason: 'Blocked or challenge page detected',
        finalUrl,
        httpStatus,
        checkedAt: new Date().toISOString(),
      };
    }
    if (contentStatus === 'BROKEN') {
      return {
        status: 'BROKEN',
        reason: 'Broken page marker detected',
        finalUrl,
        httpStatus,
        checkedAt: new Date().toISOString(),
      };
    }

    if (!bodyText.trim()) {
      return {
        status: 'UNCERTAIN',
        reason: 'Empty page body',
        finalUrl,
        httpStatus,
        checkedAt: new Date().toISOString(),
      };
    }

    return {
      status: 'WORKING',
      reason: 'Page loaded successfully',
      finalUrl,
      httpStatus,
      checkedAt: new Date().toISOString(),
    };
  });
}

export { classifyContent };
