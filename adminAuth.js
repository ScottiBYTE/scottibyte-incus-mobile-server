const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const speakeasy = require('speakeasy');
const { db } = require('./db');

function ensureAdminAuthTables() {
  db.prepare(`
    CREATE TABLE IF NOT EXISTS app_settings (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL,
      updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )
  `).run();

  db.prepare(`
    CREATE TABLE IF NOT EXISTS admin_users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      totp_secret TEXT NOT NULL,
      enabled INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      last_login_at TEXT
    )
  `).run();
}

function getSetting(key, defaultValue = null) {
  const row = db.prepare(`
    SELECT value
    FROM app_settings
    WHERE key = ?
  `).get(key);

  return row ? row.value : defaultValue;
}

function setSetting(key, value) {
  db.prepare(`
    INSERT INTO app_settings (key, value, updated_at)
    VALUES (?, ?, CURRENT_TIMESTAMP)
    ON CONFLICT(key) DO UPDATE SET
      value = excluded.value,
      updated_at = CURRENT_TIMESTAMP
  `).run(key, String(value));
}

function getOrCreateSetting(key, createValueFn) {
  const existing = getSetting(key);
  if (existing) return existing;

  const value = createValueFn();
  setSetting(key, value);
  return value;
}

function getSessionSecret() {
  return getOrCreateSetting('admin_session_secret', () => crypto.randomBytes(48).toString('hex'));
}

function getSessionHours() {
  const value = Number(getSetting('admin_session_hours', '12'));
  return Number.isFinite(value) && value > 0 ? value : 12;
}

function getAdminAccessMode() {
  return getSetting('admin_access_mode', 'lan');
}

function getAdminUserCount() {
  const row = db.prepare(`
    SELECT COUNT(*) AS count
    FROM admin_users
    WHERE enabled = 1
  `).get();

  return Number(row?.count || 0);
}

function adminSetupRequired() {
  return getAdminUserCount() === 0;
}

function getAdminUserByUsername(username) {
  return db.prepare(`
    SELECT id, username, password_hash, totp_secret, enabled
    FROM admin_users
    WHERE username = ?
      AND enabled = 1
  `).get(username);
}

function createInitialAdminUser(username, password) {
  ensureAdminAuthTables();

  if (!adminSetupRequired()) {
    throw new Error('Admin setup has already been completed');
  }

  const cleanUsername = String(username || '').trim();

  if (!/^[a-zA-Z0-9_.-]{3,64}$/.test(cleanUsername)) {
    throw new Error('Username must be 3-64 characters and may contain letters, numbers, dots, underscores, and hyphens');
  }

  if (String(password || '').length < 12) {
    throw new Error('Password must be at least 12 characters');
  }

  const secret = speakeasy.generateSecret({
    name: `ScottiBYTE Incus Mobile Server (${cleanUsername})`,
    issuer: 'ScottiBYTE'
  });

  const passwordHash = bcrypt.hashSync(String(password), 12);

  db.prepare(`
    INSERT INTO admin_users (username, password_hash, totp_secret)
    VALUES (?, ?, ?)
  `).run(cleanUsername, passwordHash, secret.base32);

  setSetting('admin_access_mode', 'lan');
  setSetting('admin_auth_enabled', 'true');
  getSessionSecret();

  return {
    username: cleanUsername,
    totp_secret: secret.base32,
    otpauth_url: secret.otpauth_url
  };
}

function requireAdminAuth(req, res, next) {
  ensureAdminAuthTables();

  if (adminSetupRequired()) {
    if (req.originalUrl && req.originalUrl.startsWith('/api/admin')) {
      return res.status(428).json({
        ok: false,
        setup_required: true,
        error: 'Initial admin setup required'
      });
    }

    return res.redirect('/admin/setup');
  }

  if (req.session && req.session.adminAuthenticated === true) {
    return next();
  }

  if (req.originalUrl && req.originalUrl.startsWith('/api/admin')) {
    return res.status(401).json({
      ok: false,
      error: 'Admin authentication required'
    });
  }

  return res.redirect('/admin/login');
}

function verifyAdminCredentials(username, password, token) {
  const user = getAdminUserByUsername(String(username || '').trim());

  if (!user) {
    return null;
  }

  const passwordOk = bcrypt.compareSync(String(password || ''), user.password_hash);

  if (!passwordOk) {
    return null;
  }

  const tokenOk = speakeasy.totp.verify({
    secret: user.totp_secret,
    encoding: 'base32',
    token: String(token || '').replace(/\s+/g, ''),
    window: 1
  });

  if (!tokenOk) {
    return null;
  }

  return user;
}


function verifyTotpForUser(username, token) {
  const user = getAdminUserByUsername(String(username || '').trim());

  if (!user) {
    return false;
  }

  return speakeasy.totp.verify({
    secret: user.totp_secret,
    encoding: 'base32',
    token: String(token || '').replace(/\s+/g, ''),
    window: 1
  });
}

function recordAdminLogin(userId) {
  db.prepare(`
    UPDATE admin_users
    SET last_login_at = CURRENT_TIMESTAMP
    WHERE id = ?
  `).run(userId);
}

module.exports = {
  ensureAdminAuthTables,
  getSessionSecret,
  getSessionHours,
  getAdminAccessMode,
  adminSetupRequired,
  createInitialAdminUser,
  requireAdminAuth,
  verifyAdminCredentials,
  verifyTotpForUser,
  recordAdminLogin
};
