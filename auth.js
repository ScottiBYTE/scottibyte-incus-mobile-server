const crypto = require('crypto');
const { db } = require('./db');

function nowIso() {
  return new Date().toISOString();
}

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

function getBearerToken(req) {
  const header = String(req.headers.authorization || '');
  const match = header.match(/^Bearer\s+(.+)$/i);
  return match ? match[1].trim() : null;
}

function validateMobileToken(req, res, next) {
  const token = getBearerToken(req);

  if (!token) {
    return res.status(401).json({
      ok: false,
      error: 'Missing bearer token'
    });
  }

  const tokenHash = hashToken(token);

  const client = db.prepare(`
    SELECT
      id,
      device_id,
      device_name,
      status,
      role
    FROM mobile_clients
    WHERE token_hash = ?
      AND status = 'approved'
  `).get(tokenHash);

  if (!client) {
    return res.status(401).json({
      ok: false,
      error: 'Invalid or revoked token'
    });
  }

  db.prepare(`
    UPDATE mobile_clients
    SET last_seen_at = ?,
        last_ip = ?
    WHERE id = ?
  `).run(nowIso(), req.ip, client.id);

  req.mobileClient = client;
  next();
}

function requireMobileAuth(req, res, next) {
  return validateMobileToken(req, res, next);
}

function allowAdminBypass(req, res, next) {
  /*
    If a bearer token is supplied, always validate it.
    If no bearer token is supplied and admin bypass is enabled, allow the request.
    This keeps the admin browser UI working while still letting Android clients authenticate.
  */
  const token = getBearerToken(req);

  if (token) {
    return validateMobileToken(req, res, next);
  }

  if (process.env.ADMIN_BYPASS_MOBILE_AUTH === 'true') {
    return next();
  }

  return res.status(401).json({
    ok: false,
    error: 'Missing bearer token'
  });
}

module.exports = {
  requireMobileAuth,
  allowAdminBypass
};
