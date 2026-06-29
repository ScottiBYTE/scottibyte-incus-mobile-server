const { WebSocketServer } = require('ws');
const pty = require('node-pty');
const { validateMobileBearerTokenValue } = require('./auth');
const { getAllInstances } = require('./incus');
const { logAuditEvent } = require('./audit');

function nowIso() {
  return new Date().toISOString();
}

function terminalEnabled() {
  return String(process.env.MOBILE_TERMINAL_ENABLED || 'false').toLowerCase() === 'true';
}

function getProtectedInstances() {
  return String(process.env.MOBILE_PROTECTED_INSTANCES || '')
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean);
}

function isProtectedTarget(target) {
  const protectedList = getProtectedInstances();

  return protectedList.some((value) => {
    return value === target.name ||
      value === target.raw ||
      target.raw.endsWith(`:${value}`);
  });
}

function parseInstanceTarget(value) {
  const raw = String(value || '').trim();
  const parts = raw.split(':');

  if (parts.length < 3) {
    throw new Error('Terminal target must use remote:project:name format');
  }

  const [remote, project, ...nameParts] = parts;
  const name = nameParts.join(':');

  if (!remote || !project || !name) {
    throw new Error('Terminal target must include remote, project, and name');
  }

  if (!/^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,62}$/.test(remote)) {
    throw new Error('Invalid remote name');
  }

  if (!/^[a-zA-Z0-9_.-]{1,64}$/.test(project)) {
    throw new Error('Invalid project name');
  }

  if (!/^[a-zA-Z0-9_.:-]{1,128}$/.test(name)) {
    throw new Error('Invalid instance name');
  }

  return { raw, remote, project, name };
}

function sendJson(ws, payload) {
  if (ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

function getActorName(client) {
  return client?.display_name ||
    client?.device_name ||
    client?.device_id ||
    'mobile-client';
}

async function validateTerminalRequest(req) {
  const url = new URL(req.url, 'http://localhost');
  const token = url.searchParams.get('token');
  const targetId = url.searchParams.get('target');

  const client = validateMobileBearerTokenValue(
    token,
    req.socket?.remoteAddress || null
  );

  if (!client) {
    throw Object.assign(new Error('Invalid or revoked token'), { status: 401 });
  }

  if (client.role !== 'admin') {
    throw Object.assign(new Error('Admin role required for shell access'), { status: 403 });
  }

  if (!terminalEnabled()) {
    throw Object.assign(new Error('Mobile terminal disabled'), { status: 403 });
  }

  const target = parseInstanceTarget(targetId);

  if (isProtectedTarget(target)) {
    throw Object.assign(new Error(`Protected instance: ${target.name}`), { status: 403 });
  }

  const instances = await getAllInstances();
  const instance = instances.find((item) => {
    return !item.error &&
      item.remote === target.remote &&
      item.project === target.project &&
      item.name === target.name;
  });

  if (!instance) {
    throw Object.assign(new Error('Instance not found'), { status: 404 });
  }

  if (instance.type !== 'container') {
    throw Object.assign(new Error('Shell is only available for containers'), { status: 400 });
  }

  if (instance.status !== 'Running') {
    throw Object.assign(new Error('Shell is only available for running containers'), { status: 400 });
  }

  return { client, target, instance };
}

function attachMobileTerminal(server) {
  const wss = new WebSocketServer({ noServer: true });

  server.on('upgrade', (req, socket, head) => {
    let pathname = '';

    try {
      pathname = new URL(req.url, 'http://localhost').pathname;
    } catch {
      pathname = '';
    }

    if (pathname !== '/api/mobile/terminal') {
      return;
    }

    wss.handleUpgrade(req, socket, head, (ws) => {
      wss.emit('connection', ws, req);
    });
  });

  wss.on('connection', async (ws, req) => {
    let child = null;
    let sessionInfo = null;
    let idleTimer = null;

    const resetIdleTimer = () => {
      if (idleTimer) {
        clearTimeout(idleTimer);
      }

      const timeoutMs = Number(process.env.MOBILE_TERMINAL_IDLE_TIMEOUT_MS || 15 * 60 * 1000);

      idleTimer = setTimeout(() => {
        sendJson(ws, {
          type: 'status',
          message: 'Terminal idle timeout. Closing session.'
        });

        try {
          ws.close();
        } catch {
        }
      }, timeoutMs);
    };

    const cleanup = (reason) => {
      if (idleTimer) {
        clearTimeout(idleTimer);
        idleTimer = null;
      }

      if (child) {
        try {
          child.kill();
        } catch {
        }
        child = null;
      }

      if (sessionInfo) {
        logAuditEvent({
          actor_type: 'mobile_client',
          actor_id: String(sessionInfo.client.id),
          actor_name: getActorName(sessionInfo.client),
          event_type: 'terminal.closed',
          target_type: 'instance',
          target_id: sessionInfo.target.raw,
          result: 'info',
          message: reason || 'Terminal session closed',
          metadata: {
            target: sessionInfo.target,
            closed_at: nowIso()
          }
        });
      }
    };

    try {
      sessionInfo = await validateTerminalRequest(req);

      logAuditEvent({
        actor_type: 'mobile_client',
        actor_id: String(sessionInfo.client.id),
        actor_name: getActorName(sessionInfo.client),
        event_type: 'terminal.opened',
        target_type: 'instance',
        target_id: sessionInfo.target.raw,
        result: 'info',
        message: `Terminal session opened for ${sessionInfo.target.raw}`,
        metadata: {
          target: sessionInfo.target,
          opened_at: nowIso()
        }
      });

      const args = [
        'exec',
        `${sessionInfo.target.remote}:${sessionInfo.target.name}`,
        '--project',
        sessionInfo.target.project,
        '--mode',
        'interactive',
        '--env',
        'TERM=xterm-256color',
        '--',
        '/bin/sh',
        '-lc',
        'if command -v bash >/dev/null 2>&1; then exec bash -l; else exec sh -l; fi'
      ];

      child = pty.spawn('incus', args, {
        name: 'xterm-256color',
        cols: 120,
        rows: 32,
        cwd: process.cwd(),
        env: {
          ...process.env,
          TERM: 'xterm-256color'
        }
      });

      sendJson(ws, {
        type: 'status',
        message: `Connected to ${sessionInfo.target.raw}`
      });

      child.onData((data) => {
        sendJson(ws, {
          type: 'output',
          data
        });
      });

      child.onExit(({ exitCode }) => {
        sendJson(ws, {
          type: 'closed',
          exitCode
        });

        try {
          ws.close();
        } catch {
        }
      });

      resetIdleTimer();

      ws.on('message', (raw) => {
        resetIdleTimer();

        let msg;

        try {
          msg = JSON.parse(String(raw));
        } catch {
          msg = { type: 'input', data: String(raw) };
        }

        if (msg.type === 'input') {
          child.write(String(msg.data || ''));
          return;
        }

        if (msg.type === 'resize') {
          const cols = Number(msg.cols || 120);
          const rows = Number(msg.rows || 32);

          if (cols >= 20 && cols <= 300 && rows >= 5 && rows <= 120) {
            child.resize(cols, rows);
          }
          return;
        }

        if (msg.type === 'exit') {
          ws.close();
        }
      });

      ws.on('close', () => cleanup('Terminal websocket closed'));
      ws.on('error', () => cleanup('Terminal websocket error'));
    } catch (err) {
      sendJson(ws, {
        type: 'error',
        error: err.message || 'Terminal connection failed'
      });

      try {
        ws.close();
      } catch {
      }

      if (sessionInfo) {
        cleanup(err.message);
      }
    }
  });
}

module.exports = {
  attachMobileTerminal
};
