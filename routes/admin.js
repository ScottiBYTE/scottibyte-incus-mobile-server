const express = require('express');
const crypto = require('crypto');
const { db } = require('../db');

const router = express.Router();

function nowIso() {
  return new Date().toISOString();
}

function makeToken() {
  return crypto.randomBytes(32).toString('hex');
}

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

router.get('/clients', (req, res) => {
  const clients = db.prepare(`
    SELECT
      id,
      device_id,
      device_name,
      app_version,
      status,
      role,
      created_at,
      approved_at,
      revoked_at,
      last_seen_at,
      last_ip,
      token_claimed_at
    FROM mobile_clients
    ORDER BY
      CASE status
        WHEN 'pending' THEN 1
        WHEN 'approved' THEN 2
        WHEN 'revoked' THEN 3
        ELSE 4
      END,
      created_at DESC
  `).all();

  res.json({
    ok: true,
    clients
  });
});

router.post('/clients/:id/approve', (req, res) => {
  const id = Number(req.params.id);
  const requestedRole = String(req.body.role || 'viewer').trim();

  const role = ['viewer', 'operator'].includes(requestedRole)
    ? requestedRole
    : 'viewer';

  const client = db.prepare(`
    SELECT id, device_id, device_name
    FROM mobile_clients
    WHERE id = ?
  `).get(id);

  if (!client) {
    return res.status(404).json({
      ok: false,
      error: 'Client not found'
    });
  }

  const token = makeToken();
  const tokenHash = hashToken(token);

  db.prepare(`
    UPDATE mobile_clients
    SET status = 'approved',
        role = ?,
        token_hash = ?,
        token_once = ?,
        token_claimed_at = NULL,
        approved_at = ?,
        revoked_at = NULL
    WHERE id = ?
  `).run(role, tokenHash, token, nowIso(), id);

  res.json({
    ok: true,
    id,
    device_id: client.device_id,
    device_name: client.device_name,
    status: 'approved',
    role,
    message: 'Device approved. The phone will receive its token the next time it checks pairing status.'
  });
});

router.post('/clients/:id/revoke', (req, res) => {
  const id = Number(req.params.id);

  const result = db.prepare(`
    UPDATE mobile_clients
    SET status = 'revoked',
        token_hash = NULL,
        token_once = NULL,
        revoked_at = ?
    WHERE id = ?
  `).run(nowIso(), id);

  if (result.changes === 0) {
    return res.status(404).json({
      ok: false,
      error: 'Client not found'
    });
  }

  res.json({
    ok: true,
    id,
    status: 'revoked'
  });
});

router.post('/clients/:id/role', (req, res) => {
  const id = Number(req.params.id);
  const requestedRole = String(req.body.role || '').trim();

  if (!['viewer', 'operator'].includes(requestedRole)) {
    return res.status(400).json({
      ok: false,
      error: 'Role must be viewer or operator'
    });
  }

  const result = db.prepare(`
    UPDATE mobile_clients
    SET role = ?
    WHERE id = ?
  `).run(requestedRole, id);

  if (result.changes === 0) {
    return res.status(404).json({
      ok: false,
      error: 'Client not found'
    });
  }

  res.json({
    ok: true,
    id,
    role: requestedRole
  });
});

module.exports = router;
