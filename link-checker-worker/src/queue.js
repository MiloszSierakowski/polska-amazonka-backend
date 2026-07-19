export class SingleFlightQueue {
  constructor(maxPending = Number.POSITIVE_INFINITY) {
    this.running = false;
    this.pending = [];
    this.maxPending = maxPending;
  }

  getPendingCount() {
    return this.pending.length;
  }

  isFull() {
    return this.running && this.pending.length >= this.maxPending;
  }

  enqueue(task) {
    if (this.isFull()) {
      return Promise.reject(new QueueFullError());
    }
    return new Promise((resolve, reject) => {
      this.pending.push({ task, resolve, reject });
      this.#drain();
    });
  }

  #drain() {
    if (this.running || this.pending.length === 0) {
      return;
    }

    this.running = true;
    const { task, resolve, reject } = this.pending.shift();

    Promise.resolve()
      .then(task)
      .then(resolve, reject)
      .finally(() => {
        this.running = false;
        this.#drain();
      });
  }
}

export class QueueFullError extends Error {
  constructor() {
    super('QUEUE_FULL');
    this.name = 'QueueFullError';
  }
}
