import { chromium } from 'playwright';
import { config } from './config.js';

let browserPromise;

export async function getBrowser() {
  if (!browserPromise) {
    browserPromise = chromium.launch({ headless: config.headless });
  }
  return browserPromise;
}

export async function closeBrowser() {
  if (!browserPromise) {
    return;
  }
  const browser = await browserPromise;
  browserPromise = undefined;
  await browser.close();
}

export async function withPage(callback) {
  const browser = await getBrowser();
  const context = await browser.newContext({
    userAgent:
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    locale: 'pl-PL',
  });
  const page = await context.newPage();
  try {
    return await callback(page);
  } finally {
    await context.close();
  }
}
