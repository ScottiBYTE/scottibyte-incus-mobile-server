const express = require('express');
const crypto = require('crypto');
const { db } = require('../db');
const { getRemoteInventory, addRemoteViaSsh, removeRemote, testRemote } = require('../incus');
const { logAuditEvent, listAuditEvents, getAdminActor } = require('../audit');
const {
  listAllOperationDefinitions,
  setOperationEnabled,
  setOperationRole
} = require('../operations');

const router = express.Router();

router.get('/audit-events', (req, res) => {
  try {
    const limit = Number(req.query.limit || 50);

    res.json({
      ok: true,
      events: listAuditEvents(limit)
    });
  } catch (err) {
    res.status(500).json({
      ok: false,
      error: err.message
    });
  }
});


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
      display_name,
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

  logAuditEvent({
    ...getAdminActor(req),
    event_type: 'client.approve',
    target_type: 'mobile_client',
    target_id: String(id),
    result: 'success',
    message: `Approved mobile client ${client.device_name || client.device_id} as ${role}`,
    metadata: {
      device_id: client.device_id,
      device_name: client.device_name,
      role
    }
  });

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


router.post('/clients/:id/rename', (req, res) => {
  const id = Number(req.params.id);
  const displayName = String(req.body.display_name ?? '').trim();

  if (displayName.length > 80) {
    return res.status(400).json({
      ok: false,
      error: 'Display name must be 80 characters or less'
    });
  }

  const client = db.prepare(`
    SELECT id, device_id, device_name, display_name
    FROM mobile_clients
    WHERE id = ?
  `).get(id);

  if (!client) {
    return res.status(404).json({
      ok: false,
      error: 'Client not found'
    });
  }

  db.prepare(`
    UPDATE mobile_clients
    SET display_name = ?
    WHERE id = ?
  `).run(displayName || null, id);

  logAuditEvent({
    ...getAdminActor(req),
    event_type: 'client.rename',
    target_type: 'mobile_client',
    target_id: String(id),
    result: 'success',
    message: `Renamed mobile client ${client.device_name || client.device_id}`,
    metadata: {
      device_id: client.device_id,
      device_name: client.device_name,
      old_display_name: client.display_name,
      new_display_name: displayName || null
    }
  });

  res.json({
    ok: true,
    id,
    display_name: displayName || null
  });
});


router.post('/clients/:id/revoke', (req, res) => {
  const id = Number(req.params.id);

  const client = db.prepare(`
    SELECT id, device_id, device_name, display_name, status, role
    FROM mobile_clients
    WHERE id = ?
  `).get(id);

  if (!client) {
    return res.status(404).json({
      ok: false,
      error: 'Client not found'
    });
  }

  db.prepare(`
    UPDATE mobile_clients
    SET status = 'revoked',
        token_hash = NULL,
        token_once = NULL,
        revoked_at = ?
    WHERE id = ?
  `).run(nowIso(), id);

  logAuditEvent({
    ...getAdminActor(req),
    event_type: 'client.revoke',
    target_type: 'mobile_client',
    target_id: String(id),
    result: 'success',
    message: `Revoked mobile client ${client.display_name || client.device_name || client.device_id}`,
    metadata: {
      device_id: client.device_id,
      device_name: client.device_name,
      display_name: client.display_name,
      previous_status: client.status,
      role: client.role
    }
  });

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

  const client = db.prepare(`
    SELECT id, device_id, device_name, display_name, role
    FROM mobile_clients
    WHERE id = ?
  `).get(id);

  if (!client) {
    return res.status(404).json({
      ok: false,
      error: 'Client not found'
    });
  }

  db.prepare(`
    UPDATE mobile_clients
    SET role = ?
    WHERE id = ?
  `).run(requestedRole, id);

  logAuditEvent({
    ...getAdminActor(req),
    event_type: 'client.role_change',
    target_type: 'mobile_client',
    target_id: String(id),
    result: 'success',
    message: `Changed mobile client role to ${requestedRole}`,
    metadata: {
      device_id: client.device_id,
      device_name: client.device_name,
      display_name: client.display_name,
      old_role: client.role,
      new_role: requestedRole
    }
  });

  res.json({
    ok: true,
    id,
    role: requestedRole
  });
});


router.get('/remotes', async (req, res) => {
  try {
    const inventory = await getRemoteInventory();

    res.json({
      ok: true,
      managed: inventory.managed,
      ignored: inventory.ignored
    });
  } catch (err) {
    res.status(500).json({
      ok: false,
      error: err.message
    });
  }
});

router.post('/remotes', async (req, res) => {
  try {
    const result = await addRemoteViaSsh({
      name: req.body.name,
      host: req.body.host || req.body.addr,
      incus_port: req.body.incus_port,
      ssh_user: req.body.ssh_user,
      ssh_port: req.body.ssh_port,
      ssh_password: req.body.ssh_password,
      trust_name: req.body.trust_name
    });

    logAuditEvent({
      ...getAdminActor(req),
      event_type: 'remote.add',
      target_type: 'remote',
      target_id: result.name,
      result: 'success',
      message: `Added remote ${result.name}`,
      metadata: {
        addr: result.addr
      }
    });

    res.json({
      ok: true,
      remote: result
    });
  } catch (err) {
    logAuditEvent({
      ...getAdminActor(req),
      event_type: 'remote.add',
      target_type: 'remote',
      target_id: req.body?.name || null,
      result: 'failed',
      message: err.message
    });

    res.status(400).json({
      ok: false,
      error: err.message
    });
  }
});

router.post('/remotes/:name/test', async (req, res) => {
  try {
    const result = await testRemote(req.params.name);

    logAuditEvent({
      ...getAdminActor(req),
      event_type: 'remote.test',
      target_type: 'remote',
      target_id: req.params.name,
      result: result.ok ? 'success' : 'failed',
      message: result.ok
        ? `Remote ${req.params.name} is reachable`
        : `Remote ${req.params.name} test failed`,
      metadata: result
    });

    res.status(result.ok ? 200 : 502).json({
      ok: result.ok,
      test: result
    });
  } catch (err) {
    logAuditEvent({
      ...getAdminActor(req),
      event_type: 'remote.test',
      target_type: 'remote',
      target_id: req.params.name,
      result: 'failed',
      message: err.message
    });

    res.status(400).json({
      ok: false,
      error: err.message
    });
  }
});

router.delete('/remotes/:name', async (req, res) => {
  try {
    const name = String(req.params.name || '').trim();

    if (['local', 'images'].includes(name)) {
      return res.status(400).json({
        ok: false,
        error: `Remote "${name}" is reserved and cannot be deleted here`
      });
    }

    const result = await removeRemote(name);

    logAuditEvent({
      ...getAdminActor(req),
      event_type: 'remote.delete',
      target_type: 'remote',
      target_id: name,
      result: 'success',
      message: `Deleted remote ${name}`
    });

    res.json({
      ok: true,
      result
    });
  } catch (err) {
    res.status(400).json({
      ok: false,
      error: err.message
    });
  }
});




router.get('/operations-preview', (req, res) => {
  try {
    const roles = ['viewer', 'operator', 'admin'];

    res.json({
      ok: true,
      preview: roles.map((role) => ({
        role,
        operations: listOperationDefinitionsForRole(role)
      }))
    });
  } catch (err) {
    res.status(500).json({
      ok: false,
      error: err.message
    });
  }
});

router.get('/operations', (req, res) => {
  try {
    res.json({
      ok: true,
      operations: listAllOperationDefinitions()
    });
  } catch (err) {
    res.status(500).json({
      ok: false,
      error: err.message
    });
  }
});

router.post('/operations/:operationKey/enabled', (req, res) => {
  const operationKey = req.params.operationKey;
  const enabled = Boolean(req.body?.enabled);

  try {
    const before = listAllOperationDefinitions().find((op) => op.operation_key === operationKey);
    const op = setOperationEnabled(operationKey, enabled);

    logAuditEvent({
      ...getAdminActor(req),
      event_type: 'operation_definition.enabled',
      target_type: 'operation_definition',
      target_id: operationKey,
      result: 'success',
      message: `${enabled ? 'Enabled' : 'Disabled'} operation ${operationKey}`,
      metadata: {
        operation: operationKey,
        previous_enabled: before?.enabled,
        enabled
      }
    });

    res.json({
      ok: true,
      operation: op
    });
  } catch (err) {
    logAuditEvent({
      ...getAdminActor(req),
      event_type: 'operation_definition.enabled',
      target_type: 'operation_definition',
      target_id: operationKey,
      result: 'failed',
      message: err.message,
      metadata: {
        operation: operationKey,
        enabled
      }
    });

    res.status(400).json({
      ok: false,
      error: err.message
    });
  }
});

router.post('/operations/:operationKey/role', (req, res) => {
  const operationKey = req.params.operationKey;
  const roleRequired = String(req.body?.role_required || '').trim();

  try {
    const before = listAllOperationDefinitions().find((op) => op.operation_key === operationKey);
    const op = setOperationRole(operationKey, roleRequired);

    logAuditEvent({
      ...getAdminActor(req),
      event_type: 'operation_definition.role',
      target_type: 'operation_definition',
      target_id: operationKey,
      result: 'success',
      message: `Changed ${operationKey} required role to ${roleRequired}`,
      metadata: {
        operation: operationKey,
        previous_role_required: before?.role_required,
        role_required: roleRequired
      }
    });

    res.json({
      ok: true,
      operation: op
    });
  } catch (err) {
    logAuditEvent({
      ...getAdminActor(req),
      event_type: 'operation_definition.role',
      target_type: 'operation_definition',
      target_id: operationKey,
      result: 'failed',
      message: err.message,
      metadata: {
        operation: operationKey,
        role_required: roleRequired
      }
    });

    res.status(400).json({
      ok: false,
      error: err.message
    });
  }
});


module.exports = router;
