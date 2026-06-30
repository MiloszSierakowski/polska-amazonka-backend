export class SingleFlightQueue {
  constructor() {
    this.running = false;
    this.pending = [];
  }

  enqueue(task) {
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
