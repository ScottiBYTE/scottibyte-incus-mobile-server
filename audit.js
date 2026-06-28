const { db } = require('./db');

function ensureAuditTable() {
  db.prepare(`
    CREATE TABLE IF NOT EXISTS audit_events (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      actor_type TEXT NOT NULL DEFAULT 'system',
      actor_id TEXT,
      actor_name TEXT,
      event_type TEXT NOT NULL,
      target_type TEXT,
      target_id TEXT,
      result TEXT NOT NULL DEFAULT 'info',
      message TEXT,
      metadata_json TEXT
    )
  `).run();

  db.prepare(`
    CREATE INDEX IF NOT EXISTS idx_audit_events_created_at
    ON audit_events (created_at DESC)
  `).run();

  db.prepare(`
    CREATE INDEX IF NOT EXISTS idx_audit_events_event_type
    ON audit_events (event_type)
  `).run();

  db.prepare(`
    CREATE INDEX IF NOT EXISTS idx_audit_events_target
    ON audit_events (target_type, target_id)
  `).run();
}

function logAuditEvent(event = {}) {
  ensureAuditTable();

  const metadata = event.metadata == null
    ? null
    : JSON.stringify(event.metadata);

  db.prepare(`
    INSERT INTO audit_events (
      actor_type,
      actor_id,
      actor_name,
      event_type,
      target_type,
      target_id,
      result,
      message,
      metadata_json
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    String(event.actor_type || 'system'),
    event.actor_id == null ? null : String(event.actor_id),
    event.actor_name == null ? null : String(event.actor_name),
    String(event.event_type || 'unknown'),
    event.target_type == null ? null : String(event.target_type),
    event.target_id == null ? null : String(event.target_id),
    String(event.result || 'info'),
    event.message == null ? null : String(event.message),
    metadata
  );
}

function listAuditEvents(limit = 50) {
  ensureAuditTable();

  const safeLimit = Math.max(1, Math.min(Number(limit || 50), 200));

  return db.prepare(`
    SELECT
      id,
      created_at,
      actor_type,
      actor_id,
      actor_name,
      event_type,
      target_type,
      target_id,
      result,
      message,
      metadata_json
    FROM audit_events
    ORDER BY id DESC
    LIMIT ?
  `).all(safeLimit).map((row) => ({
    ...row,
    metadata: row.metadata_json ? JSON.parse(row.metadata_json) : null
  }));
}

function getAdminActor(req) {
  return {
    actor_type: 'admin',
    actor_id: req.session?.adminUserId == null ? null : String(req.session.adminUserId),
    actor_name: req.session?.adminUsername || 'admin'
  };
}

module.exports = {
  ensureAuditTable,
  logAuditEvent,
  listAuditEvents,
  getAdminActor
};
