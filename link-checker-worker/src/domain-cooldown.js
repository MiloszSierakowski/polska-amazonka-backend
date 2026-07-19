export class DomainCooldown {
  constructor(durationMs, now = () => Date.now()) {
    this.durationMs = durationMs;
    this.now = now;
    this.blockedUntil = new Map();
  }

  isBlocked(url) {
    const domain = new URL(url).hostname.toLowerCase();
    const until = this.blockedUntil.get(domain);
    if (until === undefined) {
      return false;
    }
    if (until <= this.now()) {
      this.blockedUntil.delete(domain);
      return false;
    }
    return true;
  }

  block(url) {
    const domain = new URL(url).hostname.toLowerCase();
    this.blockedUntil.set(domain, this.now() + this.durationMs);
  }
}
