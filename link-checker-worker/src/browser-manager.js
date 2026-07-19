import { chromium } from 'playwright';
import { config } from './config.js';
import { withTotalTimeout } from './total-timeout.js';

export function createBrowserLifecycle(launchBrowser) {
  let browserPromise;
  let currentBrowser;

  async function getBrowser() {
    if (!browserPromise) {
      let launchPromise;
      launchPromise = Promise.resolve()
        .then(() => launchBrowser({ headless: config.headless }))
        .then((browser) => {
          if (browserPromise === launchPromise) {
            currentBrowser = browser;
            browser.on?.('disconnected', () => {
              if (currentBrowser === browser) {
                currentBrowser = undefined;
                browserPromise = undefined;
              }
            });
          }
          return browser;
        })
        .catch((error) => {
          if (browserPromise === launchPromise) {
            currentBrowser = undefined;
            browserPromise = undefined;
          }
          throw error;
        });
      browserPromise = launchPromise;
    }
    return browserPromise;
  }

  async function closeBrowser() {
    const pendingBrowser = browserPromise;
    browserPromise = undefined;
    currentBrowser = undefined;
    if (!pendingBrowser) {
      return;
    }
    let browser;
    try {
      browser = await pendingBrowser;
    } catch {
      return;
    }
    try {
      if (typeof browser.isConnected !== 'function' || browser.isConnected()) {
        await browser.close();
      }
    } catch {
      // Closing an already disconnected browser is harmless.
    }
  }

  return { getBrowser, closeBrowser };
}

const browserLifecycle = createBrowserLifecycle((options) => chromium.launch(options));

export const getBrowser = browserLifecycle.getBrowser;
export const closeBrowser = browserLifecycle.closeBrowser;

export async function withPage(callback) {
  let context;
  let page;

  const closeResources = async () => {
    await page?.close().catch(() => {});
    await context?.close().catch(() => {});
  };

  return withTotalTimeout(
    async () => {
      const browser = await getBrowser();
      context = await browser.newContext({
        userAgent:
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
        locale: 'pl-PL',
      });
      page = await context.newPage();
      return callback(page);
    },
    config.checkTotalTimeoutMs,
    closeResources,
  );
}
