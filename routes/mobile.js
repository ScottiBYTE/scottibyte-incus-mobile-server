const express = require('express');
const { getMobileActionsStatus } = require('../operations');
const { getAllInstances, getRemotes, runInstanceAction, listInstanceSnapshots, createInstanceSnapshot } = require('../incus');
const { requireMobileAuth } = require('../auth');

const router = express.Router();


const ANDROID_VERSION_INFO = {
  android_version: '0.5.0',
  server_release: 'v1.4.0',
  apk_url: 'https://github.com/ScottiBYTE/scottibyte-incus-mobile-server/releases/download/v1.4.0/ScottiBYTE-Incus-Mobile-Android-v0.5.0-debug.apk',
  release_url: 'https://github.com/ScottiBYTE/scottibyte-incus-mobile-server/releases/tag/v1.4.0'
};

router.get('/android-version', async (req, res) => {
  res.json({
    ok: true,
    ...ANDROID_VERSION_INFO
  });
});

router.get('/health', async (req, res) => {
  res.json({
    ok: true,
    app: process.env.APP_NAME || 'ScottiBYTE Incus Mobile Server',
    actions_enabled: getMobileActionsStatus().effective_enabled,
    mobile_actions: getMobileActionsStatus(),
    app_time_zone: process.env.APP_TIME_ZONE || null,
    time: new Date().toISOString()
  });
});

router.get('/remotes', requireMobileAuth, async (req, res) => {
  try {
    const remotes = await getRemotes();
    res.json({ ok: true, remotes });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

router.get('/instances', requireMobileAuth, async (req, res) => {
  try {
    const instances = await getAllInstances();

    res.json({
      ok: true,
      generated_at: new Date().toISOString(),
      count: instances.filter(i => !i.error).length,
      instances
    });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

router.get('/summary', requireMobileAuth, async (req, res) => {
  try {
    const instances = await getAllInstances();
    const valid = instances.filter(i => !i.error);

    const running = valid.filter(i => i.status === 'Running').length;
    const stopped = valid.filter(i => i.status === 'Stopped').length;
    const notRunning = valid.length - running;

    const client = req.mobileClient || null;

    res.json({
      ok: true,
      generated_at: new Date().toISOString(),
      client: client ? {
        id: client.id,
        device_id: client.device_id,
        device_name: client.device_name,
        display_name: client.display_name || client.device_name || client.device_id,
        status: client.status,
        role: client.role
      } : null,
      summary: {
        containers_total: valid.filter(i => i.type === 'container').length,
        virtual_machines_total: valid.filter(i => i.type === 'virtual-machine').length,
        instances_total: valid.length,
        running,
        stopped,
        not_running: notRunning,
        errors: instances.filter(i => i.error).length
      }
    });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});


router.get('/instances/:id', requireMobileAuth, async (req, res) => {
  try {
    const requestedId = decodeURIComponent(req.params.id);
    const instances = await getAllInstances();
    const instance = instances.find(i => i.id === requestedId);

    if (!instance) {
      return res.status(404).json({
        ok: false,
        error: 'Instance not found',
        id: requestedId
      });
    }

    res.json({
      ok: true,
      generated_at: new Date().toISOString(),
      instance
    });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});


function getProtectedInstances() {
  const raw = process.env.MOBILE_PROTECTED_INSTANCES || '';
  return raw.split(',').map(s => s.trim()).filter(Boolean);
}

function isProtectedInstance(instance) {
  const protectedList = getProtectedInstances();

  return protectedList.some(item => {
    return item === instance.name || item === instance.id;
  });
}

function canRunAction(client, instance, action) {
  if (!getMobileActionsStatus().effective_enabled) {
    return { ok: false, reason: 'Mobile actions disabled' };
  }

  if (!client || !['operator', 'admin'].includes(client.role)) {
    return { ok: false, reason: 'Operator or admin role required' };
  }

  if (isProtectedInstance(instance)) {
    return { ok: false, reason: 'Protected instance' };
  }

  if (!['start', 'stop', 'restart'].includes(action)) {
    return { ok: false, reason: 'Unsupported action' };
  }

  if (action === 'start' && instance.status === 'Running') {
    return { ok: false, reason: 'Instance is already running' };
  }

  if ((action === 'stop' || action === 'restart') && instance.status !== 'Running') {
    return { ok: false, reason: 'Instance is not running' };
  }

  return { ok: true, reason: null };
}

async function findInstanceById(id) {
  const instances = await getAllInstances();
  return instances.find(i => i.id === id);
}


function requireSnapshotAdmin(req, res) {
  const client = req.mobileClient;

  if (!client || client.role !== 'admin') {
    res.status(403).json({
      ok: false,
      error: 'Admin role required'
    });
    return false;
  }

  return true;
}

router.get('/instances/:id/snapshots', requireMobileAuth, async (req, res) => {
  try {
    if (!requireSnapshotAdmin(req, res)) return;

    const requestedId = decodeURIComponent(req.params.id);
    const instance = await findInstanceById(requestedId);

    if (!instance) {
      return res.status(404).json({
        ok: false,
        error: 'Instance not found',
        id: requestedId
      });
    }

    if (isProtectedInstance(instance)) {
      return res.status(403).json({
        ok: false,
        error: 'Protected instance',
        id: requestedId
      });
    }

    const snapshots = await listInstanceSnapshots(requestedId);

    res.json({
      ok: true,
      id: requestedId,
      snapshots
    });
  } catch (err) {
    res.status(500).json({
      ok: false,
      error: err.message
    });
  }
});

router.post('/instances/:id/snapshots', requireMobileAuth, async (req, res) => {
  try {
    if (!requireSnapshotAdmin(req, res)) return;

    const requestedId = decodeURIComponent(req.params.id);
    const instance = await findInstanceById(requestedId);

    if (!instance) {
      return res.status(404).json({
        ok: false,
        error: 'Instance not found',
        id: requestedId
      });
    }

    if (isProtectedInstance(instance)) {
      return res.status(403).json({
        ok: false,
        error: 'Protected instance',
        id: requestedId
      });
    }

    const result = await createInstanceSnapshot(requestedId, req.body?.name);

    res.json({
      ok: true,
      id: requestedId,
      snapshot: result.snapshot,
      message: `Snapshot created: ${result.snapshot}`
    });
  } catch (err) {
    res.status(500).json({
      ok: false,
      error: err.message
    });
  }
});


router.post('/instances/:id/actions/:action', requireMobileAuth, async (req, res) => {
  try {
    const requestedId = decodeURIComponent(req.params.id);
    const action = String(req.params.action || '').trim();

    const instance = await findInstanceById(requestedId);

    if (!instance) {
      return res.status(404).json({
        ok: false,
        error: 'Instance not found',
        id: requestedId
      });
    }

    const allowed = canRunAction(req.mobileClient, instance, action);

    if (!allowed.ok) {
      return res.status(403).json({
        ok: false,
        error: allowed.reason,
        id: requestedId,
        action
      });
    }

    const result = await runInstanceAction(requestedId, action);

    res.json({
      ok: true,
      id: requestedId,
      action,
      result,
      message: `${action} command sent`
    });
  } catch (err) {
    res.status(500).json({
      ok: false,
      error: err.message
    });
  }
});


module.exports = router;
