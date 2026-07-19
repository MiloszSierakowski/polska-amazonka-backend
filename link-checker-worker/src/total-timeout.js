export class CheckTimeoutError extends Error {
  constructor() {
    super('CHECK_TIMEOUT');
    this.name = 'CheckTimeoutError';
  }
}

export async function withTotalTimeout(operation, timeoutMs, cleanup = async () => {}) {
  let timer;
  let cleanupPromise;
  const cleanupOnce = () => {
    cleanupPromise ??= Promise.resolve().then(cleanup);
    return cleanupPromise;
  };
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => {
      reject(new CheckTimeoutError());
      void cleanupOnce();
    }, timeoutMs);
  });

  try {
    return await Promise.race([operation(), timeout]);
  } finally {
    clearTimeout(timer);
    await cleanupOnce();
  }
}
