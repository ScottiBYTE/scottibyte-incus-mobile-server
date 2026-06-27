const express = require('express');
const { db } = require('../db');

const router = express.Router();

function nowIso() {
  return new Date().toISOString();
}

router.post('/request', (req, res) => {
  const deviceId = String(req.body.device_id || '').trim();
  const deviceName = String(req.body.device_name || '').trim();
  const appVersion = String(req.body.app_version || '').trim();

  if (!deviceId || !deviceName) {
    return res.status(400).json({
      ok: false,
      error: 'device_id and device_name are required'
    });
  }

  const existing = db.prepare(`
    SELECT id, device_id, device_name, status, role
    FROM mobile_clients
    WHERE device_id = ?
  `).get(deviceId);

  if (existing) {
    return res.json({
      ok: true,
      status: existing.status,
      role: existing.role,
      message: existing.status === 'pending'
        ? 'Device is pending approval'
        : `Device is ${existing.status}`
    });
  }

  db.prepare(`
    INSERT INTO mobile_clients (
      device_id,
      device_name,
      app_version,
      status,
      role,
      created_at,
      last_ip
    ) VALUES (?, ?, ?, 'pending', 'viewer', ?, ?)
  `).run(
    deviceId,
    deviceName,
    appVersion || null,
    nowIso(),
    req.ip
  );

  res.json({
    ok: true,
    status: 'pending',
    message: 'Device pairing request created'
  });
});

router.get('/status/:device_id', (req, res) => {
  const deviceId = String(req.params.device_id || '').trim();

  const client = db.prepare(`
    SELECT
      id,
      device_id,
      device_name,
      status,
      role,
      token_once
    FROM mobile_clients
    WHERE device_id = ?
  `).get(deviceId);

  if (!client) {
    return res.status(404).json({
      ok: false,
      error: 'Device not found'
    });
  }

  if (client.status === 'approved' && client.token_once) {
    const token = client.token_once;
    const now = nowIso();

    db.prepare(`
      UPDATE mobile_clients
      SET token_once = NULL,
          token_claimed_at = ?,
          last_seen_at = ?,
          last_ip = ?
      WHERE id = ?
    `).run(now, now, req.ip, client.id);

    return res.json({
      ok: true,
      device_id: client.device_id,
      device_name: client.device_name,
      status: client.status,
      role: client.role,
      token,
      token_type: 'Bearer',
      message: 'Device approved. Token returned once.'
    });
  }

  res.json({
    ok: true,
    device_id: client.device_id,
    device_name: client.device_name,
    status: client.status,
    role: client.role,
    message: client.status === 'approved'
      ? 'Device already approved and token has already been claimed'
      : `Device is ${client.status}`
  });
});

module.exports = router;
