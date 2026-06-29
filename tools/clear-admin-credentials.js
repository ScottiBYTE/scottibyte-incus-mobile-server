#!/usr/bin/env node

/**
 * ScottiBYTE Incus Mobile Server
 * Local admin account recovery tool
 *
 * This resets only the web admin credentials and admin sessions.
 * It intentionally does NOT modify:
 * - mobile clients
 * - Incus server/remotes
 * - app settings
 * - operation policies
 * - audit history
 */

const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');

const projectRoot = path.resolve(__dirname, '..');
const dbPath = process.env.DB_PATH || path.join(projectRoot, 'data', 'mobile.db');

function nowIso() {
  return new Date().toISOString();
}

function tableExists(db, tableName) {
  const row = db.prepare(`
    SELECT name
    FROM sqlite_master
    WHERE type = 'table'
      AND name = ?
  `).get(tableName);

  return !!row;
}

function countRows(db, tableName) {
  if (!tableExists(db, tableName)) return 0;
  const row = db.prepare(`SELECT COUNT(*) AS count FROM ${tableName}`).get();
  return row?.count || 0;
}

function insertAuditEvent(db, details) {
  if (!tableExists(db, 'audit_events')) return;

  const columns = db.prepare(`PRAGMA table_info(audit_events)`).all().map((c) => c.name);
  const has = (name) => columns.includes(name);

  const event = {
    created_at: nowIso(),
    actor_type: 'cli_recovery',
    actor_id: 'local',
    actor_name: 'Local recovery tool',
    event_type: 'admin.credentials.reset',
    target_type: 'admin',
    target_id: 'admin',
    result: 'success',
    message: 'Admin credentials reset from local server recovery tool',
    metadata_json: JSON.stringify({
      tool: 'tools/clear-admin-credentials.js',
      preserved: [
        'mobile_clients',
        'remotes',
        'app_settings',
        'operation_definitions',
        'audit_events'
      ]
    })
  };

  const insertColumns = Object.keys(event).filter(has);

  if (!insertColumns.length) return;

  const placeholders = insertColumns.map((name) => `@${name}`).join(', ');
  const sql = `
    INSERT INTO audit_events (${insertColumns.join(', ')})
    VALUES (${placeholders})
  `;

  db.prepare(sql).run(event);
}

if (!fs.existsSync(dbPath)) {
  console.error(`Database not found: ${dbPath}`);
  process.exit(1);
}

const db = new Database(dbPath);

try {
  const beforeUsers = countRows(db, 'admin_users');
  const beforeSessions = countRows(db, 'admin_sessions');

  const reset = db.transaction(() => {
    if (tableExists(db, 'admin_users')) {
      db.prepare('DELETE FROM admin_users').run();
    }

    if (tableExists(db, 'admin_sessions')) {
      db.prepare('DELETE FROM admin_sessions').run();
    }

    /*
     * Future-proofing:
     * If recovery-code tables are added later, clear them here without failing
     * on current installations.
     */
    for (const table of ['admin_recovery_codes', 'admin_totp_recovery_codes']) {
      if (tableExists(db, table)) {
        db.prepare(`DELETE FROM ${table}`).run();
      }
    }

    insertAuditEvent(db);
  });

  reset();

  const afterUsers = countRows(db, 'admin_users');
  const afterSessions = countRows(db, 'admin_sessions');

  console.log('ScottiBYTE Incus Mobile Server admin credentials reset complete.');
  console.log('');
  console.log(`Database: ${dbPath}`);
  console.log(`Admin users: ${beforeUsers} -> ${afterUsers}`);
  console.log(`Admin sessions: ${beforeSessions} -> ${afterSessions}`);
  console.log('');
  console.log('Preserved mobile clients, Incus servers, app settings, operation policy, and audit history.');
  console.log('Visit /admin to complete first-run admin setup again.');
} catch (err) {
  console.error('Admin credential reset failed.');
  console.error(err && err.stack ? err.stack : String(err));
  process.exit(1);
} finally {
  db.close();
}
