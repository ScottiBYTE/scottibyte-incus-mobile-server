const { execFile } = require('child_process');
const { db } = require('./db');
const { logAuditEvent } = require('./audit');

const ROLE_RANK = {
  viewer: 10,
  operator: 20,
  admin: 30
};

function nowIso() {
  return new Date().toISOString();
}

function ensureOperationTables() {
  db.prepare(`
    CREATE TABLE IF NOT EXISTS operation_definitions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      operation_key TEXT NOT NULL UNIQUE,
      label TEXT NOT NULL,
      description TEXT,
      enabled INTEGER NOT NULL DEFAULT 0,
      role_required TEXT NOT NULL DEFAULT 'operator',
      target_type TEXT NOT NULL,
      runner_type TEXT NOT NULL DEFAULT 'incus_cli',
      argv_template_json TEXT NOT NULL,
      allowed_params_json TEXT NOT NULL DEFAULT '[]',
      required_params_json TEXT NOT NULL DEFAULT '[]',
      protected_target_policy TEXT NOT NULL DEFAULT 'instance_name',
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )
  `).run();

  db.prepare(`
    CREATE INDEX IF NOT EXISTS idx_operation_definitions_key
    ON operation_definitions (operation_key)
  `).run();

  db.prepare(`
    CREATE INDEX IF NOT EXISTS idx_operation_definitions_enabled
    ON operation_definitions (enabled)
  `).run();
}

function upsertDefaultOperation(def) {
  db.prepare(`
    INSERT INTO operation_definitions (
      operation_key,
      label,
      description,
      enabled,
      role_required,
      target_type,
      runner_type,
      argv_template_json,
      allowed_params_json,
      required_params_json,
      protected_target_policy,
      updated_at
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(operation_key) DO NOTHING
  `).run(
    def.operation_key,
    def.label,
    def.description || null,
    def.enabled ? 1 : 0,
    def.role_required,
    def.target_type,
    def.runner_type,
    JSON.stringify(def.argv_template),
    JSON.stringify(def.allowed_params || []),
    JSON.stringify(def.required_params || []),
    def.protected_target_policy || 'instance_name',
    nowIso()
  );
}

function seedDefaultOperations() {
  ensureOperationTables();

  const defaults = [
    {
      operation_key: 'instance.start',
      label: 'Start Instance',
      description: 'Start a stopped Incus container or virtual machine.',
      enabled: false,
      role_required: 'operator',
      target_type: 'instance',
      runner_type: 'incus_cli',
      argv_template: ['start', '{{target.remote}}:{{target.name}}', '--project', '{{target.project}}'],
      protected_target_policy: 'instance_name'
    },
    {
      operation_key: 'instance.stop',
      label: 'Stop Instance',
      description: 'Stop a running Incus container or virtual machine.',
      enabled: false,
      role_required: 'operator',
      target_type: 'instance',
      runner_type: 'incus_cli',
      argv_template: ['stop', '{{target.remote}}:{{target.name}}', '--project', '{{target.project}}'],
      protected_target_policy: 'instance_name'
    },
    {
      operation_key: 'instance.restart',
      label: 'Restart Instance',
      description: 'Restart a running Incus container or virtual machine.',
      enabled: false,
      role_required: 'operator',
      target_type: 'instance',
      runner_type: 'incus_cli',
      argv_template: ['restart', '{{target.remote}}:{{target.name}}', '--project', '{{target.project}}'],
      protected_target_policy: 'instance_name'
    },
    {
      operation_key: 'instance.shell',
      label: 'Shell',
      description: 'Open an interactive shell into a running Incus container.',
      enabled: false,
      role_required: 'admin',
      target_type: 'instance',
      runner_type: 'websocket_terminal',
      argv_template: ['shell', '{{target.remote}}:{{target.name}}', '--project', '{{target.project}}'],
      protected_target_policy: 'instance_name'
    }
  ];

  defaults.forEach(upsertDefaultOperation);
}

function parseJsonArray(value, fallback = []) {
  try {
    const parsed = JSON.parse(value || '[]');
    return Array.isArray(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

function parseOperationRow(row) {
  if (!row) return null;

  return {
    ...row,
    enabled: Boolean(row.enabled),
    argv_template: parseJsonArray(row.argv_template_json),
    allowed_params: parseJsonArray(row.allowed_params_json),
    required_params: parseJsonArray(row.required_params_json)
  };
}

function getOperationDefinition(operationKey) {
  ensureOperationTables();

  const row = db.prepare(`
    SELECT *
    FROM operation_definitions
    WHERE operation_key = ?
  `).get(operationKey);

  return parseOperationRow(row);
}

function listOperationDefinitionsForRole(role) {
  ensureOperationTables();

  const actorRank = ROLE_RANK[role] || 0;

  return db.prepare(`
    SELECT *
    FROM operation_definitions
    WHERE enabled = 1
    ORDER BY operation_key
  `).all()
    .map(parseOperationRow)
    .filter((op) => actorRank >= (ROLE_RANK[op.role_required] || 999))
    .map((op) => ({
      operation: op.operation_key,
      label: op.label,
      description: op.description,
      role_required: op.role_required,
      target_type: op.target_type,
      allowed_params: op.allowed_params,
      required_params: op.required_params
    }));
}

function validateOperationKey(value) {
  const operation = String(value || '').trim();

  if (!/^[a-z][a-z0-9_.-]{1,80}$/.test(operation)) {
    throw new Error('Invalid operation key');
  }

  return operation;
}

function validateTargetType(value) {
  const targetType = String(value || '').trim();

  if (!/^[a-z][a-z0-9_.-]{1,40}$/.test(targetType)) {
    throw new Error('Invalid target type');
  }

  return targetType;
}

function validateParams(params, allowedParams, requiredParams) {
  const clean = params && typeof params === 'object' && !Array.isArray(params)
    ? params
    : {};

  const allowed = new Set(allowedParams || []);
  const required = requiredParams || [];

  for (const key of Object.keys(clean)) {
    if (!/^[a-zA-Z0-9_.-]{1,64}$/.test(key)) {
      throw new Error(`Invalid parameter name: ${key}`);
    }

    if (!allowed.has(key)) {
      throw new Error(`Parameter is not allowed for this operation: ${key}`);
    }

    const value = clean[key];

    if (
      value !== null &&
      typeof value !== 'string' &&
      typeof value !== 'number' &&
      typeof value !== 'boolean'
    ) {
      throw new Error(`Parameter must be a string, number, boolean, or null: ${key}`);
    }
  }

  for (const key of required) {
    if (!(key in clean) || clean[key] === '' || clean[key] == null) {
      throw new Error(`Missing required parameter: ${key}`);
    }
  }

  return clean;
}

function parseInstanceTarget(targetId) {
  const raw = String(targetId || '').trim();
  const parts = raw.split(':');

  if (parts.length < 3) {
    throw new Error('Instance target must use remote:project:name format');
  }

  const [remote, project, ...nameParts] = parts;
  const name = nameParts.join(':');

  if (!remote || !project || !name) {
    throw new Error('Instance target must include remote, project, and name');
  }

  return {
    raw,
    remote,
    project,
    name
  };
}

function parseTarget(targetType, targetId) {
  if (targetType === 'instance') {
    return parseInstanceTarget(targetId);
  }

  return {
    raw: String(targetId || '').trim(),
    id: String(targetId || '').trim()
  };
}

function checkProtectedTarget(operation, target) {
  if (operation.protected_target_policy !== 'instance_name') {
    return;
  }

  if (!target || !target.name) {
    return;
  }

  const protectedInstances = String(process.env.MOBILE_PROTECTED_INSTANCES || '')
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean);

  const hit = protectedInstances.find((name) => name === target.name || target.raw.endsWith(`:${name}`));

  if (hit) {
    throw new Error(`Protected instance: ${hit}`);
  }
}

function renderTemplateValue(template, context) {
  const value = String(template);

  const rendered = value.replace(/\{\{\s*([a-zA-Z0-9_.]+)\s*\}\}/g, (match, keyPath) => {
    const parts = keyPath.split('.');
    let current = context;

    for (const part of parts) {
      if (current == null || !(part in current)) {
        throw new Error(`Template variable not found: ${keyPath}`);
      }

      current = current[part];
    }

    if (current == null) {
      return '';
    }

    return String(current);
  });

  if (rendered.includes('{{') || rendered.includes('}}')) {
    throw new Error(`Invalid unresolved template: ${value}`);
  }

  return rendered;
}

function buildArgv(operation, context) {
  if (!Array.isArray(operation.argv_template) || operation.argv_template.length === 0) {
    throw new Error('Operation has no argv template');
  }

  return operation.argv_template.map((item) => renderTemplateValue(item, context));
}

function runIncusArgv(argv, timeoutMs = 60000) {
  return new Promise((resolve, reject) => {
    execFile('incus', argv, { timeout: timeoutMs }, (error, stdout, stderr) => {
      if (error) {
        reject(new Error(stderr || stdout || error.message));
        return;
      }

      resolve({
        stdout: stdout || '',
        stderr: stderr || ''
      });
    });
  });
}

function roleAllowed(actorRole, requiredRole) {
  return (ROLE_RANK[actorRole] || 0) >= (ROLE_RANK[requiredRole] || 999);
}

function getMobileActor(req) {
  const client = req.mobileClient;

  return {
    actor_type: 'mobile_client',
    actor_id: client?.id == null ? null : String(client.id),
    actor_name: client?.display_name || client?.device_name || client?.device_id || 'mobile-client',
    role: client?.role || 'viewer'
  };
}


function dryRunOperationRequest(request, simulatedRole = 'operator') {
  const role = String(simulatedRole || 'operator').trim();

  if (!Object.prototype.hasOwnProperty.call(ROLE_RANK, role)) {
    throw new Error('Invalid simulated role');
  }

  const operationKey = validateOperationKey(request.operation);
  const targetType = validateTargetType(request.target_type);
  const targetId = String(request.target_id || '').trim();

  if (!targetId) {
    throw new Error('target_id is required');
  }

  const operation = getOperationDefinition(operationKey);

  if (!operation) {
    return {
      allowed: false,
      reason: 'Unknown operation',
      operation: operationKey,
      target_type: targetType,
      target_id: targetId
    };
  }

  if (!operation.enabled) {
    return {
      allowed: false,
      reason: 'Operation disabled',
      operation: operationKey,
      target_type: targetType,
      target_id: targetId
    };
  }

  if (operation.target_type !== targetType) {
    return {
      allowed: false,
      reason: `Operation requires target_type ${operation.target_type}`,
      operation: operationKey,
      target_type: targetType,
      target_id: targetId
    };
  }

  if (!roleAllowed(role, operation.role_required)) {
    return {
      allowed: false,
      reason: `Role ${role} cannot run ${operationKey}; ${operation.role_required} required`,
      operation: operationKey,
      target_type: targetType,
      target_id: targetId
    };
  }

  let params;
  try {
    params = validateParams(request.params, operation.allowed_params, operation.required_params);
  } catch (err) {
    return {
      allowed: false,
      reason: err.message,
      operation: operationKey,
      target_type: targetType,
      target_id: targetId
    };
  }

  let target;
  try {
    target = parseTarget(targetType, targetId);
  } catch (err) {
    return {
      allowed: false,
      reason: err.message,
      operation: operationKey,
      target_type: targetType,
      target_id: targetId
    };
  }

  try {
    checkProtectedTarget(operation, target);
  } catch (err) {
    return {
      allowed: false,
      reason: err.message,
      operation: operationKey,
      target_type: targetType,
      target_id: targetId
    };
  }

  if (operation.runner_type !== 'incus_cli') {
    return {
      allowed: false,
      reason: `Unsupported operation runner: ${operation.runner_type}`,
      operation: operationKey,
      target_type: targetType,
      target_id: targetId
    };
  }

  let argv;
  try {
    argv = buildArgv(operation, {
      operation: operationKey,
      target,
      params
    });
  } catch (err) {
    return {
      allowed: false,
      reason: err.message,
      operation: operationKey,
      target_type: targetType,
      target_id: targetId
    };
  }

  return {
    allowed: true,
    reason: 'Operation would be allowed',
    operation: operationKey,
    label: operation.label,
    role,
    role_required: operation.role_required,
    target_type: targetType,
    target_id: targetId,
    params,
    runner_type: operation.runner_type,
    argv
  };
}

async function executeOperationRequest(req, request) {
  const actor = getMobileActor(req);
  const operationKey = validateOperationKey(request.operation);
  const targetType = validateTargetType(request.target_type);
  const targetId = String(request.target_id || '').trim();

  if (!targetId) {
    throw new Error('target_id is required');
  }

  const operation = getOperationDefinition(operationKey);

  if (!operation) {
    logAuditEvent({
      actor_type: actor.actor_type,
      actor_id: actor.actor_id,
      actor_name: actor.actor_name,
      event_type: 'operation.blocked',
      target_type: targetType,
      target_id: targetId,
      result: 'blocked',
      message: `Unknown operation: ${operationKey}`,
      metadata: {
        operation: operationKey,
        role: actor.role
      }
    });

    return {
      ok: false,
      status: 404,
      error: 'Unknown operation'
    };
  }

  const mobileActionsEnabled = String(process.env.MOBILE_ACTIONS_ENABLED || 'false').toLowerCase() === 'true';

  if (!mobileActionsEnabled) {
    logAuditEvent({
      actor_type: actor.actor_type,
      actor_id: actor.actor_id,
      actor_name: actor.actor_name,
      event_type: 'operation.blocked',
      target_type: targetType,
      target_id: targetId,
      result: 'blocked',
      message: `Mobile operations are globally disabled: ${operationKey}`,
      metadata: {
        operation: operationKey,
        role: actor.role
      }
    });

    return {
      ok: false,
      status: 403,
      error: 'Mobile operations disabled'
    };
  }

  if (!operation.enabled) {
    logAuditEvent({
      actor_type: actor.actor_type,
      actor_id: actor.actor_id,
      actor_name: actor.actor_name,
      event_type: 'operation.blocked',
      target_type: targetType,
      target_id: targetId,
      result: 'blocked',
      message: `Operation is disabled: ${operationKey}`,
      metadata: {
        operation: operationKey,
        role: actor.role
      }
    });

    return {
      ok: false,
      status: 403,
      error: 'Operation disabled'
    };
  }

  if (operation.target_type !== targetType) {
    throw new Error(`Operation requires target_type ${operation.target_type}`);
  }

  if (!roleAllowed(actor.role, operation.role_required)) {
    logAuditEvent({
      actor_type: actor.actor_type,
      actor_id: actor.actor_id,
      actor_name: actor.actor_name,
      event_type: 'operation.blocked',
      target_type: targetType,
      target_id: targetId,
      result: 'blocked',
      message: `Role ${actor.role} cannot run ${operationKey}`,
      metadata: {
        operation: operationKey,
        role: actor.role,
        role_required: operation.role_required
      }
    });

    return {
      ok: false,
      status: 403,
      error: 'Role not permitted for this operation'
    };
  }

  const params = validateParams(request.params, operation.allowed_params, operation.required_params);
  const target = parseTarget(targetType, targetId);

  try {
    checkProtectedTarget(operation, target);
  } catch (err) {
    logAuditEvent({
      actor_type: actor.actor_type,
      actor_id: actor.actor_id,
      actor_name: actor.actor_name,
      event_type: 'operation.blocked',
      target_type: targetType,
      target_id: targetId,
      result: 'blocked',
      message: err.message,
      metadata: {
        operation: operationKey,
        role: actor.role,
        params
      }
    });

    return {
      ok: false,
      status: 403,
      error: err.message
    };
  }

  if (operation.runner_type !== 'incus_cli') {
    throw new Error(`Unsupported operation runner: ${operation.runner_type}`);
  }

  const argv = buildArgv(operation, {
    operation: operationKey,
    target,
    params
  });

  logAuditEvent({
    actor_type: actor.actor_type,
    actor_id: actor.actor_id,
    actor_name: actor.actor_name,
    event_type: 'operation.request',
    target_type: targetType,
    target_id: targetId,
    result: 'info',
    message: `Requested ${operationKey}`,
    metadata: {
      operation: operationKey,
      role: actor.role,
      params
    }
  });

  try {
    const result = await runIncusArgv(argv);

    logAuditEvent({
      actor_type: actor.actor_type,
      actor_id: actor.actor_id,
      actor_name: actor.actor_name,
      event_type: 'operation.success',
      target_type: targetType,
      target_id: targetId,
      result: 'success',
      message: `${operationKey} completed`,
      metadata: {
        operation: operationKey,
        argv,
        stdout: result.stdout,
        stderr: result.stderr
      }
    });

    return {
      ok: true,
      status: 200,
      operation: operationKey,
      target_type: targetType,
      target_id: targetId,
      result
    };
  } catch (err) {
    logAuditEvent({
      actor_type: actor.actor_type,
      actor_id: actor.actor_id,
      actor_name: actor.actor_name,
      event_type: 'operation.failed',
      target_type: targetType,
      target_id: targetId,
      result: 'failed',
      message: err.message,
      metadata: {
        operation: operationKey,
        argv
      }
    });

    return {
      ok: false,
      status: 500,
      error: err.message,
      operation: operationKey,
      target_type: targetType,
      target_id: targetId
    };
  }
}


function listAllOperationDefinitions() {
  ensureOperationTables();

  return db.prepare(`
    SELECT *
    FROM operation_definitions
    ORDER BY operation_key
  `).all().map(parseOperationRow).map((op) => ({
    id: op.id,
    operation_key: op.operation_key,
    label: op.label,
    description: op.description,
    enabled: op.enabled,
    role_required: op.role_required,
    target_type: op.target_type,
    runner_type: op.runner_type,
    argv_template: op.argv_template,
    allowed_params: op.allowed_params,
    required_params: op.required_params,
    protected_target_policy: op.protected_target_policy,
    created_at: op.created_at,
    updated_at: op.updated_at
  }));
}

function setOperationEnabled(operationKey, enabled) {
  ensureOperationTables();

  const op = getOperationDefinition(operationKey);

  if (!op) {
    throw new Error('Operation not found');
  }

  db.prepare(`
    UPDATE operation_definitions
    SET enabled = ?, updated_at = ?
    WHERE operation_key = ?
  `).run(enabled ? 1 : 0, nowIso(), operationKey);

  return getOperationDefinition(operationKey);
}

function setOperationRole(operationKey, roleRequired) {
  ensureOperationTables();

  const role = String(roleRequired || '').trim();

  if (!['operator', 'admin'].includes(role)) {
    throw new Error('Operation role must be operator or admin');
  }

  const op = getOperationDefinition(operationKey);

  if (!op) {
    throw new Error('Operation not found');
  }

  db.prepare(`
    UPDATE operation_definitions
    SET role_required = ?, updated_at = ?
    WHERE operation_key = ?
  `).run(role, nowIso(), operationKey);

  return getOperationDefinition(operationKey);
}

module.exports = {
  ensureOperationTables,
  seedDefaultOperations,
  listOperationDefinitionsForRole,
  listAllOperationDefinitions,
  setOperationEnabled,
  setOperationRole,
  dryRunOperationRequest,
  executeOperationRequest
};
