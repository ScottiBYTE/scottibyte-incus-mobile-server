const bcrypt = require('bcryptjs');
const speakeasy = require('speakeasy');

const { initDb, db } = require('../db');
const { ensureAdminAuthTables } = require('../adminAuth');

const username = String(process.argv[2] || '').trim();
const password = String(process.env.SCOTTIBYTE_ADMIN_RESET_PASSWORD || '');

if (!username) {
  console.error('Usage: SCOTTIBYTE_ADMIN_RESET_PASSWORD="new password" node scripts/reset-admin.js <username>');
  process.exit(1);
}

if (password.length < 12) {
  console.error('Password must be provided in SCOTTIBYTE_ADMIN_RESET_PASSWORD and must be at least 12 characters.');
  process.exit(1);
}

if (!/^[a-zA-Z0-9_.-]{3,64}$/.test(username)) {
  console.error('Username must be 3-64 characters and may contain letters, numbers, dots, underscores, and hyphens.');
  process.exit(1);
}

initDb();
ensureAdminAuthTables();

const passwordHash = bcrypt.hashSync(password, 12);

const secret = speakeasy.generateSecret({
  name: `ScottiBYTE Incus Mobile Server (${username})`,
  issuer: 'ScottiBYTE'
});

const existing = db.prepare(`
  SELECT id
  FROM admin_users
  WHERE username = ?
`).get(username);

if (existing) {
  db.prepare(`
    UPDATE admin_users
    SET password_hash = ?,
        totp_secret = ?,
        enabled = 1
    WHERE username = ?
  `).run(passwordHash, secret.base32, username);
} else {
  db.prepare(`
    INSERT INTO admin_users (username, password_hash, totp_secret, enabled)
    VALUES (?, ?, ?, 1)
  `).run(username, passwordHash, secret.base32);
}

db.prepare(`
  UPDATE admin_users
  SET enabled = CASE WHEN username = ? THEN 1 ELSE 0 END
`).run(username);

db.prepare(`
  DELETE FROM admin_sessions
`).run();

db.prepare(`
  INSERT INTO app_settings (key, value, updated_at)
  VALUES ('admin_auth_enabled', 'true', CURRENT_TIMESTAMP)
  ON CONFLICT(key) DO UPDATE SET
    value = excluded.value,
    updated_at = CURRENT_TIMESTAMP
`).run();

db.prepare(`
  INSERT INTO app_settings (key, value, updated_at)
  VALUES ('admin_access_mode', 'lan', CURRENT_TIMESTAMP)
  ON CONFLICT(key) DO UPDATE SET
    value = excluded.value,
    updated_at = CURRENT_TIMESTAMP
`).run();

console.log('');
console.log('Admin account reset complete.');
console.log('');
console.log(`Username: ${username}`);
console.log('');
console.log('Add this new 2FA secret to your authenticator app:');
console.log('');
console.log(`Manual Secret: ${secret.base32}`);
console.log('');
console.log('Advanced otpauth URL:');
console.log(secret.otpauth_url);
console.log('');
console.log('Existing admin sessions were invalidated.');
console.log('Restart the service, then log in with the new password and 2FA code.');
console.log('');
