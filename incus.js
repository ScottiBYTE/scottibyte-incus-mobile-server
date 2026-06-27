const { execFile } = require('child_process');

function runIncus(args, timeoutMs = 30000) {
  return new Promise((resolve, reject) => {
    execFile('incus', args, { timeout: timeoutMs }, (error, stdout, stderr) => {
      if (error) {
        reject(new Error(stderr || error.message));
        return;
      }
      resolve(stdout);
    });
  });
}

function getConfiguredRemoteAllowlist() {
  const raw = process.env.INCUS_REMOTES || '';
  return raw.split(',').map(s => s.trim()).filter(Boolean);
}

async function getRemotes() {
  const stdout = await runIncus(['remote', 'list', '--format', 'json']);
  const data = JSON.parse(stdout);
  const allowlist = getConfiguredRemoteAllowlist();

  return Object.entries(data)
    .filter(([name, remote]) => {
      const protocol = remote.Protocol || remote.protocol;
      const isPublic = remote.Public ?? remote.public;
      const isStatic = remote.Static ?? remote.static;

      if (name === 'images') return false;
      if (name === 'local') return false;
      if (isPublic) return false;
      if (isStatic) return false;
      if (protocol !== 'incus') return false;
      if (allowlist.length > 0 && !allowlist.includes(name)) return false;

      return true;
    })
    .map(([name, remote]) => ({
      name,
      addr: remote.Addr || remote.addr,
      protocol: remote.Protocol || remote.protocol,
      auth_type: remote.AuthType || remote.auth_type || null,
      project: remote.Project || remote.project || 'default'
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

async function getInstancesForRemote(remote) {
  const stdout = await runIncus([
    'list',
    `${remote.name}:`,
    '--format',
    'json'
  ]);

  const instances = JSON.parse(stdout);
  return instances.map(inst => normalizeInstance(remote.name, remote.project || 'default', inst));
}

function normalizeInstance(remoteName, remoteProject, inst) {
  const project = inst.project || remoteProject || 'default';
  const network = inst.state?.network || {};
  const primaryNic = network.eth0 || findBestNic(network);

  const ipv4 = getIpv4Addresses(primaryNic);
  const ipv6 = getIpv6Addresses(primaryNic);
  const mac = primaryNic?.hwaddr || getConfigMac(inst) || null;

  const memoryUsed = numberOrNull(inst.state?.memory?.usage);
  const memoryTotal = getMemoryTotal(inst);

  const diskUsed = numberOrNull(inst.state?.disk?.root?.usage);
  const diskTotal =
    numberOrNull(inst.state?.disk?.root?.total) ||
    getConfiguredDiskSizeBytes(inst);

  return {
    id: `${remoteName}:${project}:${inst.name}`,
    remote: remoteName,
    project,
    name: inst.name,
    dns_name: (inst.name || '').toLowerCase(),
    type: inst.type || 'container',
    status: inst.status,
    status_code: inst.status_code,
    architecture: inst.architecture || null,
    profiles: inst.profiles || [],
    ipv4,
    ipv6,
    primary_ipv4: ipv4[0] || null,
    mac,
    cpu: {
      usage_ns: numberOrNull(inst.state?.cpu?.usage),
      allocated_time_ns: numberOrNull(inst.state?.cpu?.allocated_time),
      percent: null
    },
    memory: {
      used_bytes: memoryUsed,
      total_bytes: memoryTotal,
      used_display: formatBytes(memoryUsed),
      total_display: formatBytes(memoryTotal),
      display: formatUsedTotal(memoryUsed, memoryTotal)
    },
    disk: {
      used_bytes: diskUsed,
      total_bytes: diskTotal,
      used_display: formatBytes(diskUsed),
      total_display: formatBytes(diskTotal),
      display: formatUsedTotal(diskUsed, diskTotal)
    },
    processes: numberOrNull(inst.state?.processes),
    pid: numberOrNull(inst.state?.pid),
    started_at: cleanZeroDate(inst.state?.started_at),
    created_at: cleanZeroDate(inst.created_at),
    last_used_at: cleanZeroDate(inst.last_used_at),
    autostart: getBoolConfig(inst, 'boot.autostart'),
    limits: {
      cpu: getConfigValue(inst, 'limits.cpu'),
      cpu_allowance: getConfigValue(inst, 'limits.cpu.allowance'),
      memory: getConfigValue(inst, 'limits.memory')
    },
    backups: {
      count: Array.isArray(inst.backups) ? inst.backups.length : 0,
      last: getLastBackup(inst.backups)
    },
    snapshots: {
      count: Array.isArray(inst.snapshots) ? inst.snapshots.length : 0
    },
    actions: getAllowedActions(inst)
  };
}

function findBestNic(network) {
  const interfaces = Object.entries(network || {});
  const candidates = interfaces
    .filter(([name, iface]) => {
      if (!iface || iface.type === 'loopback') return false;
      if (name.startsWith('docker')) return false;
      if (name.startsWith('br-')) return false;
      if (name.startsWith('veth')) return false;
      return true;
    })
    .map(([, iface]) => iface);

  return candidates[0] || null;
}

function getIpv4Addresses(iface) {
  if (!iface?.addresses) return [];
  return iface.addresses
    .filter(addr => addr.family === 'inet' && addr.scope !== 'local')
    .map(addr => addr.address);
}

function getIpv6Addresses(iface) {
  if (!iface?.addresses) return [];
  return iface.addresses
    .filter(addr => addr.family === 'inet6' && addr.scope === 'global')
    .map(addr => addr.address);
}

function getConfigMac(inst) {
  return inst.config?.['volatile.eth0.hwaddr'] ||
    inst.expanded_config?.['volatile.eth0.hwaddr'] ||
    null;
}

function getMemoryTotal(inst) {
  const configured = getConfigValue(inst, 'limits.memory');
  const configuredBytes = parseSizeToBytes(configured);

  if (configuredBytes) return configuredBytes;

  return null;
}

function getConfiguredDiskSizeBytes(inst) {
  const rootSize = inst.devices?.root?.size || inst.expanded_devices?.root?.size || null;
  return parseSizeToBytes(rootSize);
}

function getLastBackup(backups) {
  if (!Array.isArray(backups) || backups.length === 0) return null;

  const sorted = [...backups].sort((a, b) => {
    return new Date(b.created_at).getTime() - new Date(a.created_at).getTime();
  });

  const backup = sorted[0];

  return {
    name: backup.name,
    created_at: cleanZeroDate(backup.created_at),
    instance_only: backup.instance_only,
    optimized_storage: backup.optimized_storage
  };
}

function getAllowedActions(inst) {
  const actionsEnabled = process.env.MOBILE_ACTIONS_ENABLED === 'true';

  if (!actionsEnabled) {
    return {
      start: false,
      stop: false,
      restart: false,
      reason: 'Mobile actions disabled'
    };
  }

  return {
    start: inst.status !== 'Running',
    stop: inst.status === 'Running',
    restart: inst.status === 'Running',
    reason: null
  };
}

function getConfigValue(inst, key) {
  return inst.config?.[key] || inst.expanded_config?.[key] || null;
}

function getBoolConfig(inst, key) {
  const value = getConfigValue(inst, key);
  if (value === null || value === undefined) return null;
  return String(value).toLowerCase() === 'true';
}

function numberOrNull(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function cleanZeroDate(value) {
  if (!value) return null;
  if (String(value).startsWith('0001-01-01')) return null;
  if (String(value).startsWith('0000-12-31')) return null;
  return value;
}

function parseSizeToBytes(value) {
  if (!value) return null;

  const raw = String(value).trim();
  const match = raw.match(/^([\d.]+)\s*([KMGTPE]?i?B?|%)?$/i);
  if (!match || raw.includes('%')) return null;

  const num = Number(match[1]);
  if (!Number.isFinite(num)) return null;

  const unit = (match[2] || 'B').toUpperCase();

  const multipliers = {
    B: 1,
    K: 1000,
    KB: 1000,
    KI: 1024,
    KIB: 1024,
    M: 1000 ** 2,
    MB: 1000 ** 2,
    MI: 1024 ** 2,
    MIB: 1024 ** 2,
    G: 1000 ** 3,
    GB: 1000 ** 3,
    GI: 1024 ** 3,
    GIB: 1024 ** 3,
    T: 1000 ** 4,
    TB: 1000 ** 4,
    TI: 1024 ** 4,
    TIB: 1024 ** 4
  };

  return Math.round(num * (multipliers[unit] || 1));
}

function formatBytes(bytes) {
  if (bytes === null || bytes === undefined) return null;

  const value = Number(bytes);
  if (!Number.isFinite(value)) return null;

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = value;
  let unit = 0;

  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit++;
  }

  const decimals = size >= 10 || unit === 0 ? 0 : 1;
  return `${size.toFixed(decimals)} ${units[unit]}`;
}

function formatUsedTotal(used, total) {
  const usedText = formatBytes(used);
  const totalText = formatBytes(total);

  if (usedText && totalText && total > 0) return `${usedText} / ${totalText}`;
  if (usedText) return usedText;
  return null;
}

async function getAllInstances() {
  const remotes = await getRemotes();
  const results = [];

  for (const remote of remotes) {
    try {
      const instances = await getInstancesForRemote(remote);
      results.push(...instances);
    } catch (err) {
      results.push({
        id: `${remote.name}:error`,
        remote: remote.name,
        error: true,
        message: err.message
      });
    }
  }

  return results.sort((a, b) => {
    if (a.error && !b.error) return 1;
    if (!a.error && b.error) return -1;

    const hostCompare = String(a.remote || '').localeCompare(String(b.remote || ''));
    if (hostCompare !== 0) return hostCompare;

    return String(a.name || '').localeCompare(String(b.name || ''));
  });
}


function parseInstanceId(id) {
  const parts = String(id || '').split(':');

  if (parts.length < 3) {
    throw new Error('Invalid instance id. Expected remote:project:name');
  }

  const remote = parts[0];
  const project = parts[1];
  const name = parts.slice(2).join(':');

  return { remote, project, name };
}

async function runInstanceAction(id, action) {
  const { remote, name } = parseInstanceId(id);

  if (!['start', 'stop', 'restart'].includes(action)) {
    throw new Error('Unsupported action');
  }

  if (action === 'restart') {
    await runIncus(['restart', `${remote}:${name}`], 60000);
  } else {
    await runIncus([action, `${remote}:${name}`], 60000);
  }

  return {
    ok: true,
    id,
    action
  };
}


module.exports = {
  getRemotes,
  getAllInstances,
  runInstanceAction,
  parseInstanceId
};
