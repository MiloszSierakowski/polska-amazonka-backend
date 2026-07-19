import { lookup } from 'node:dns/promises';
import { isIP } from 'node:net';
import { config } from './config.js';
import { isAllowedHostname, isBlockedAddress, normalizeHostname } from './network-security.js';

export class UrlValidationError extends Error {
  constructor(statusCode, reason) {
    super(reason);
    this.name = 'UrlValidationError';
    this.statusCode = statusCode;
    this.reason = reason;
  }
}

function parseUrl(rawUrl) {
  if (typeof rawUrl !== 'string' || rawUrl.trim() === '') {
    throw new UrlValidationError(400, 'INVALID_URL');
  }

  let parsed;
  try {
    parsed = new URL(rawUrl.trim());
  } catch {
    throw new UrlValidationError(400, 'INVALID_URL');
  }

  if (!['http:', 'https:'].includes(parsed.protocol) || parsed.username || parsed.password) {
    throw new UrlValidationError(400, 'INVALID_URL');
  }
  return parsed;
}

export function createUrlValidator({ allowedHosts = config.allowedShopHosts, resolve = lookup } = {}) {
  const normalizedAllowedHosts = allowedHosts.map(normalizeHostname);

  return async (rawUrl) => {
    const parsed = parseUrl(rawUrl);
    const hostname = normalizeHostname(parsed.hostname).replace(/^\[|\]$/g, '');

    if (hostname === 'localhost' || hostname.endsWith('.local')) {
      throw new UrlValidationError(403, 'PRIVATE_ADDRESS_BLOCKED');
    }
    if (isIP(hostname)) {
      const blocked = isBlockedAddress(hostname);
      throw new UrlValidationError(blocked ? 403 : 400, blocked ? 'PRIVATE_ADDRESS_BLOCKED' : 'UNSUPPORTED_HOST');
    }
    if (!isAllowedHostname(hostname, normalizedAllowedHosts)) {
      throw new UrlValidationError(400, 'UNSUPPORTED_HOST');
    }

    let addresses;
    try {
      addresses = await resolve(hostname, { all: true, verbatim: true });
    } catch {
      throw new UrlValidationError(400, 'DNS_RESOLUTION_FAILED');
    }
    if (!Array.isArray(addresses) || addresses.length === 0) {
      throw new UrlValidationError(400, 'DNS_RESOLUTION_FAILED');
    }
    if (addresses.some(({ address }) => isBlockedAddress(address))) {
      throw new UrlValidationError(403, 'PRIVATE_ADDRESS_BLOCKED');
    }

    parsed.hostname = hostname;
    return parsed.toString();
  };
}

export const validateCheckUrl = createUrlValidator();

export function shopGroup(hostname) {
  const host = normalizeHostname(hostname).replace(/^\[|\]$/g, '');
  if (host === 'allegro.pl' || host.endsWith('.allegro.pl')) {
    return 'ALLEGRO';
  }
  if (host === 'amzn.to' || host.endsWith('.amzn.to') || /(^|\.)amazon\.[a-z.]+$/.test(host)) {
    return 'AMAZON';
  }
  if (host === 'aliexpress.com' || host.endsWith('.aliexpress.com')) {
    return 'ALIEXPRESS';
  }
  if (host === 'temu.to' || host.endsWith('.temu.to') || host === 'temu.com' || host.endsWith('.temu.com')) {
    return 'TEMU';
  }
  return null;
}

export function createNavigationValidator(urlValidator = validateCheckUrl) {
  return async (candidateUrl, initialUrl) => {
    const validatedCandidate = await urlValidator(candidateUrl);
    const initialGroup = shopGroup(new URL(initialUrl).hostname);
    const candidateGroup = shopGroup(new URL(validatedCandidate).hostname);
    if (!initialGroup || initialGroup !== candidateGroup) {
      throw new UrlValidationError(403, 'REDIRECT_BLOCKED');
    }
    return validatedCandidate;
  };
}

export const validateMainNavigation = createNavigationValidator();
