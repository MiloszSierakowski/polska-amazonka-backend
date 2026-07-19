import { isIP } from 'node:net';

function ipv4Number(address) {
  return address.split('.').reduce((value, part) => (value << 8) + Number(part), 0) >>> 0;
}

function ipv4InRange(address, base, prefix) {
  const mask = prefix === 0 ? 0 : (0xffffffff << (32 - prefix)) >>> 0;
  return (ipv4Number(address) & mask) === (ipv4Number(base) & mask);
}

function blockedIpv4(address) {
  return [
    ['0.0.0.0', 8],
    ['10.0.0.0', 8],
    ['100.64.0.0', 10],
    ['127.0.0.0', 8],
    ['169.254.0.0', 16],
    ['172.16.0.0', 12],
    ['192.0.0.0', 24],
    ['192.0.2.0', 24],
    ['192.168.0.0', 16],
    ['198.18.0.0', 15],
    ['198.51.100.0', 24],
    ['203.0.113.0', 24],
    ['224.0.0.0', 4],
    ['240.0.0.0', 4],
  ].some(([base, prefix]) => ipv4InRange(address, base, prefix));
}

function expandIpv6(address) {
  const withoutZone = address.toLowerCase().split('%')[0];
  const mappedMatch = withoutZone.match(/^(.*:)(\d+\.\d+\.\d+\.\d+)$/);
  let normalized = withoutZone;
  if (mappedMatch) {
    const value = ipv4Number(mappedMatch[2]);
    normalized = `${mappedMatch[1]}${(value >>> 16).toString(16)}:${(value & 0xffff).toString(16)}`;
  }
  const [left = '', right = ''] = normalized.split('::');
  const leftParts = left ? left.split(':') : [];
  const rightParts = right ? right.split(':') : [];
  const missing = 8 - leftParts.length - rightParts.length;
  const parts = normalized.includes('::')
    ? [...leftParts, ...Array(Math.max(0, missing)).fill('0'), ...rightParts]
    : leftParts;
  if (parts.length !== 8 || parts.some((part) => !/^[0-9a-f]{1,4}$/.test(part))) {
    return null;
  }
  return parts.map((part) => Number.parseInt(part, 16));
}

function blockedIpv6(address) {
  const parts = expandIpv6(address);
  if (!parts) {
    return true;
  }
  if (parts.every((part) => part === 0) || (parts.slice(0, 7).every((part) => part === 0) && parts[7] === 1)) {
    return true;
  }
  if (
    (parts[0] & 0xfe00) === 0xfc00 ||
    (parts[0] & 0xffc0) === 0xfe80 ||
    (parts[0] & 0xffc0) === 0xfec0 ||
    (parts[0] & 0xff00) === 0xff00
  ) {
    return true;
  }
  if (
    parts.slice(0, 6).every((part) => part === 0) ||
    (parts.slice(0, 5).every((part) => part === 0) && parts[5] === 0xffff)
  ) {
    const mapped = `${parts[6] >>> 8}.${parts[6] & 255}.${parts[7] >>> 8}.${parts[7] & 255}`;
    return blockedIpv4(mapped);
  }
  return (
    (parts[0] === 0x2001 && (parts[1] === 0x0000 || parts[1] === 0x0db8)) ||
    parts[0] === 0x2002
  );
}

export function isBlockedAddress(address) {
  const version = isIP(address);
  if (version === 4) {
    return blockedIpv4(address);
  }
  if (version === 6) {
    return blockedIpv6(address);
  }
  return true;
}

export function normalizeHostname(hostname) {
  return hostname.toLowerCase().replace(/\.+$/, '');
}

export function isAllowedHostname(hostname, allowedHosts) {
  const normalized = normalizeHostname(hostname);
  return allowedHosts.some((allowedHost) => normalized === allowedHost || normalized.endsWith(`.${allowedHost}`));
}
