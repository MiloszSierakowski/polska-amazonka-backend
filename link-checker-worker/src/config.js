function positiveInteger(rawValue, fallback) {
  const parsed = Number.parseInt(rawValue ?? '', 10);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback;
}

export const config = {
  port: Number.parseInt(process.env.PORT ?? '3001', 10),
  checkTimeoutMs: positiveInteger(process.env.CHECK_TIMEOUT_MS, 30000),
  checkTotalTimeoutMs: positiveInteger(process.env.CHECK_TOTAL_TIMEOUT_MS, 45000),
  pageSettleMs: positiveInteger(process.env.PAGE_SETTLE_MS, 2000),
  domainBlockCooldownMs: positiveInteger(process.env.DOMAIN_BLOCK_COOLDOWN_MS, 600000),
  maxRequestBodyBytes: positiveInteger(process.env.MAX_REQUEST_BODY_BYTES, 16384),
  maxQueueSize: positiveInteger(process.env.MAX_QUEUE_SIZE, 50),
  allowedShopHosts: (process.env.ALLOWED_SHOP_HOSTS ??
    'allegro.pl,amazon.pl,amazon.com,amazon.de,aliexpress.com,temu.com,amzn.to,temu.to')
    .split(',')
    .map((host) => host.trim().toLowerCase().replace(/\.+$/, ''))
    .filter(Boolean),
  enableScreenshots: process.env.ENABLE_SCREENSHOTS === 'true',
  headless: process.env.HEADLESS !== 'false',
};
