function ipToLong(ip) {
  if (!ip) return null;

  // Strip IPv6-mapped IPv4 prefix.
  ip = ip.replace(/^::ffff:/, '');

  // Normalize localhost IPv6.
  if (ip === '::1') ip = '127.0.0.1';

  const parts = ip.split('.');
  if (parts.length !== 4) return null;

  const nums = parts.map((p) => Number(p));
  if (nums.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) return null;

  return (
    ((nums[0] << 24) >>> 0) +
    ((nums[1] << 16) >>> 0) +
    ((nums[2] << 8) >>> 0) +
    (nums[3] >>> 0)
  ) >>> 0;
}

function cidrContains(ip, cidr) {
  const [base, bitsRaw] = cidr.split('/');
  const bits = Number(bitsRaw);

  const ipLong = ipToLong(ip);
  const baseLong = ipToLong(base);

  if (ipLong === null || baseLong === null) return false;
  if (!Number.isInteger(bits) || bits < 0 || bits > 32) return false;

  const mask = bits === 0 ? 0 : (0xffffffff << (32 - bits)) >>> 0;
  return (ipLong & mask) === (baseLong & mask);
}

function getClientIp(req) {
  // req.ip respects Express trust proxy when enabled.
  let ip = req.ip || req.socket?.remoteAddress || '';

  if (Array.isArray(ip)) ip = ip[0];
  ip = String(ip).trim();

  // Express may return comma-separated forwarded IPs depending on proxy config.
  if (ip.includes(',')) {
    ip = ip.split(',')[0].trim();
  }

  return ip.replace(/^::ffff:/, '');
}

function getAllowedCidrs() {
  const raw = process.env.ADMIN_ALLOWED_CIDRS ||
    '127.0.0.1/32,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16';

  return raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

function isAdminIpAllowed(ip) {
  if (ip === '::1') ip = '127.0.0.1';
  ip = String(ip || '').replace(/^::ffff:/, '');

  return getAllowedCidrs().some((cidr) => cidrContains(ip, cidr));
}

function adminAccessGuard(req, res, next) {
  const ip = getClientIp(req);

  if (isAdminIpAllowed(ip)) {
    return next();
  }

  return res.status(403).json({
    ok: false,
    error: 'Admin access is restricted to local/VPN networks',
    client_ip: ip
  });
}

module.exports = {
  adminAccessGuard,
  getClientIp,
  isAdminIpAllowed
};
