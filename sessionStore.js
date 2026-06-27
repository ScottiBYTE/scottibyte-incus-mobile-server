const session = require('express-session');
const { db } = require('./db');

class BetterSqliteSessionStore extends session.Store {
  constructor(options = {}) {
    super();

    this.ttlMs = Number(options.ttlMs || 12 * 60 * 60 * 1000);

    db.prepare(`
      CREATE TABLE IF NOT EXISTS admin_sessions (
        sid TEXT PRIMARY KEY,
        expires_at INTEGER NOT NULL,
        data TEXT NOT NULL
      )
    `).run();
  }

  get(sid, callback) {
    try {
      const row = db.prepare(`
        SELECT data, expires_at
        FROM admin_sessions
        WHERE sid = ?
      `).get(sid);

      if (!row) return callback(null, null);

      if (row.expires_at <= Date.now()) {
        this.destroy(sid, () => {});
        return callback(null, null);
      }

      callback(null, JSON.parse(row.data));
    } catch (err) {
      callback(err);
    }
  }

  set(sid, sess, callback) {
    try {
      const cookieMaxAge = sess?.cookie?.maxAge;
      const ttl = Number(cookieMaxAge || this.ttlMs);
      const expiresAt = Date.now() + ttl;

      db.prepare(`
        INSERT INTO admin_sessions (sid, expires_at, data)
        VALUES (?, ?, ?)
        ON CONFLICT(sid) DO UPDATE SET
          expires_at = excluded.expires_at,
          data = excluded.data
      `).run(sid, expiresAt, JSON.stringify(sess));

      callback(null);
    } catch (err) {
      callback(err);
    }
  }

  destroy(sid, callback) {
    try {
      db.prepare(`
        DELETE FROM admin_sessions
        WHERE sid = ?
      `).run(sid);

      callback(null);
    } catch (err) {
      callback(err);
    }
  }

  touch(sid, sess, callback) {
    this.set(sid, sess, callback);
  }

  clearExpired() {
    db.prepare(`
      DELETE FROM admin_sessions
      WHERE expires_at <= ?
    `).run(Date.now());
  }
}

module.exports = {
  BetterSqliteSessionStore
};
