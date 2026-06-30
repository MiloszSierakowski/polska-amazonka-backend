export const config = {
  port: Number.parseInt(process.env.PORT ?? '3001', 10),
  checkTimeoutMs: Number.parseInt(process.env.CHECK_TIMEOUT_MS ?? '30000', 10),
  enableScreenshots: process.env.ENABLE_SCREENSHOTS === 'true',
  headless: process.env.HEADLESS !== 'false',
};
