const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');

const dataDir = process.env.DATA_DIR || './data';
fs.mkdirSync(dataDir, { recursive: true });

const dbPath = path.join(dataDir, 'mobile.db');
const db = new Database(dbPath);

function initDb() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS mobile_clients (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id TEXT UNIQUE NOT NULL,
      device_name TEXT NOT NULL,
      app_version TEXT,
      status TEXT NOT NULL DEFAULT 'pending',
      role TEXT NOT NULL DEFAULT 'viewer',
      token_hash TEXT,
      created_at TEXT NOT NULL,
      approved_at TEXT,
      revoked_at TEXT,
      last_seen_at TEXT,
      last_ip TEXT
    );

    CREATE TABLE IF NOT EXISTS action_log (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id TEXT,
      action TEXT NOT NULL,
      target_id TEXT,
      result TEXT NOT NULL,
      message TEXT,
      created_at TEXT NOT NULL
    );
  `);
}

module.exports = {
  db,
  initDb
};
