const { spawn, execFileSync } = require('child_process');
const net = require('net');
const fs = require('fs');
const path = require('path');
const { Client } = require('ssh2');
const { getMobileActionsStatus } = require('./operations');

function runIncus(args, timeoutMs = 15000) {
  return new Promise((resolve, reject) => {
    const child = spawn('incus', args, {
      stdio: ['ignore', 'pipe', 'pipe']
    });

    let stdout = '';
    let stderr = '';
    let settled = false;
    let timedOut = false;
    let forceKillTimer = null;

    const timeoutTimer = setTimeout(() => {
      timedOut = true;

      child.kill('SIGTERM');

      forceKillTimer = setTimeout(() => {
        child.kill('SIGKILL');
      }, 2000);

      // Do not keep Node alive solely for the cleanup timer.
      forceKillTimer.unref?.();

      if (!settled) {
        settled = true;
        reject(new Error(
          `Incus command timed out after ${timeoutMs} ms | argv: incus ${args.join(' ')}`
        ));
      }
    }, timeoutMs);

    child.stdout.on('data', (data) => {
      stdout += data.toString();
    });

    child.stderr.on('data', (data) => {
      stderr += data.toString();
    });

    child.on('error', (err) => {
      clearTimeout(timeoutTimer);

      if (forceKillTimer) {
        clearTimeout(forceKillTimer);
      }

      if (!settled) {
        settled = true;
        reject(err);
      }
    });

    child.on('close', (code, signal) => {
      clearTimeout(timeoutTimer);

      if (forceKillTimer) {
        clearTimeout(forceKillTimer);
      }

      if (settled || timedOut) {
        return;
      }

      if (code !== 0) {
        const details = [
          stderr ? `stderr: ${stderr.trim()}` : null,
          stdout ? `stdout: ${stdout.trim()}` : null,
          `code: ${code}`,
          signal ? `signal: ${signal}` : null,
          `argv: incus ${args.join(' ')}`
        ].filter(Boolean).join(' | ');

        settled = true;
        reject(new Error(details));
        return;
      }

      settled = true;
      resolve(stdout);
    });
  });
}

async function getRemotes() {
  const inventory = await getRemoteInventory();
  return inventory.managed.map((remote) => ({
    name: remote.name,
    addr: remote.addr,
    protocol: remote.protocol,
    auth_type: remote.auth_type,
    project: remote.project || 'default'
  }));
}

function normalizeRemoteEntry(name, remote) {
  const addrs = remote.Addrs || remote.addrs || [];
  const firstAddr = Array.isArray(addrs) && addrs.length > 0 ? addrs[0] : '';

  return {
    name,
    addr: remote.LastWorkingAddr || remote.last_working_addr || remote.Addr || remote.addr || firstAddr || '',
    protocol: remote.Protocol || remote.protocol || '',
    auth_type: remote.AuthType || remote.auth_type || '',
    project: remote.Project || remote.project || 'default',
    public: Boolean(remote.Public ?? remote.public),
    static: Boolean(remote.Static ?? remote.static),
    global: Boolean(remote.Global ?? remote.global)
  };
}

function getIgnoreReason(remote) {
  if (remote.name === 'local') return 'Ignored local/static Incus socket';
  if (remote.static) return 'Ignored static Incus remote';
  if (remote.public) return 'Ignored public remote';
  if (remote.protocol === 'simplestreams') return 'Ignored simplestreams image server';
  if (remote.protocol !== 'incus') return `Ignored unsupported protocol: ${remote.protocol || 'unknown'}`;
  if (remote.auth_type !== 'tls') return `Ignored unsupported auth type: ${remote.auth_type || 'none'}`;
  return null;
}

function isManagedRemote(remote) {
  return getIgnoreReason(remote) === null;
}

async function getRemoteInventory() {
  const stdout = await runIncus(['remote', 'list', '--format', 'json']);
  const data = JSON.parse(stdout);

  const managed = [];
  const ignored = [];

  for (const [name, raw] of Object.entries(data)) {
    const remote = normalizeRemoteEntry(name, raw);
    const reason = getIgnoreReason(remote);

    if (reason) {
      ignored.push({ ...remote, reason });
    } else {
      managed.push(remote);
    }
  }

  managed.sort((a, b) => a.name.localeCompare(b.name));
  ignored.sort((a, b) => a.name.localeCompare(b.name));

  return { managed, ignored };
}

function validateRemoteName(name) {
  const value = String(name || '').trim();

  if (!value) {
    throw new Error('Remote name is required');
  }

  if (!/^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,62}$/.test(value)) {
    throw new Error('Remote name may only contain letters, numbers, dots, underscores, and hyphens');
  }

  if (['local', 'images'].includes(value)) {
    throw new Error(`Remote name "${value}" is reserved`);
  }

  return value;
}

function normalizeHost(hostOrUrl) {
  const value = String(hostOrUrl || '').trim();

  if (!value) {
    throw new Error('Incus host or address is required');
  }

  if (value.startsWith('https://')) {
    const url = new URL(value);
    return {
      host: url.hostname,
      incusUrl: value.replace(/\/$/, '')
    };
  }

  if (value.includes('://')) {
    throw new Error('Only https:// URLs are supported for Incus remotes');
  }

  return {
    host: value,
    incusUrl: null
  };
}

function normalizePort(value, defaultPort) {
  const port = Number(value || defaultPort);

  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`Invalid port: ${value}`);
  }

  return port;
}


function getDataDir() {
  return path.resolve(__dirname, process.env.DATA_DIR || './data');
}

function getSshDir() {
  return path.join(getDataDir(), 'ssh');
}

function getPrivateKeyPath() {
  return path.join(getSshDir(), 'incus_mobile_ed25519');
}

function getPublicKeyPath() {
  return `${getPrivateKeyPath()}.pub`;
}

function sshString(value) {
  const buf = Buffer.from(value, 'utf8');
  const len = Buffer.alloc(4);
  len.writeUInt32BE(buf.length, 0);
  return Buffer.concat([len, buf]);
}

function publicKeyToOpenSsh(publicKeyObject) {
  const der = publicKeyObject.export({ type: 'spki', format: 'der' });

  // Ed25519 SPKI DER header is 12 bytes, followed by the 32-byte public key.
  const raw = der.subarray(-32);
  const type = 'ssh-ed25519';

  const body = Buffer.concat([
    sshString(type),
    sshString(raw)
  ]).toString('base64');

  return `${type} ${body} IncusMobileServer`;
}

function ensureAppSshKey() {
  const sshDir = getSshDir();
  const privateKeyPath = getPrivateKeyPath();
  const publicKeyPath = getPublicKeyPath();

  fs.mkdirSync(sshDir, { recursive: true, mode: 0o700 });

  let regenerate = false;

  if (fs.existsSync(privateKeyPath) && fs.existsSync(publicKeyPath)) {
    const existingPrivateKey = fs.readFileSync(privateKeyPath, 'utf8');

    // ssh2 expects OpenSSH private key format for Ed25519. Older generated
    // PKCS8 keys look like "BEGIN PRIVATE KEY" and are not accepted here.
    if (!existingPrivateKey.includes('BEGIN OPENSSH PRIVATE KEY')) {
      regenerate = true;
    } else {
      fs.chmodSync(privateKeyPath, 0o600);
      fs.chmodSync(publicKeyPath, 0o644);

      return {
        privateKeyPath,
        publicKeyPath,
        privateKey: existingPrivateKey,
        publicKey: fs.readFileSync(publicKeyPath, 'utf8').trim()
      };
    }
  } else {
    regenerate = true;
  }

  if (regenerate) {
    fs.rmSync(privateKeyPath, { force: true });
    fs.rmSync(publicKeyPath, { force: true });

    execFileSync('ssh-keygen', [
      '-t', 'ed25519',
      '-N', '',
      '-C', 'IncusMobileServer',
      '-f', privateKeyPath
    ], {
      stdio: 'ignore'
    });

    fs.chmodSync(privateKeyPath, 0o600);
    fs.chmodSync(publicKeyPath, 0o644);
  }

  return {
    privateKeyPath,
    publicKeyPath,
    privateKey: fs.readFileSync(privateKeyPath, 'utf8'),
    publicKey: fs.readFileSync(publicKeyPath, 'utf8').trim()
  };
}

function shellQuote(value) {
  return `'${String(value).replace(/'/g, `'\\''`)}'`;
}

function connectSsh({ host, port, username, password, privateKey, passphrase }) {
  return new Promise((resolve, reject) => {
    const conn = new Client();

    conn.on('ready', () => resolve(conn));

    conn.on('error', (err) => {
      reject(err);
    });

    const options = {
      host,
      port,
      username,
      readyTimeout: 20000
    };

    if (password) {
      options.password = password;
    }

    if (privateKey) {
      options.privateKey = privateKey;
    }

    if (passphrase) {
      options.passphrase = passphrase;
    }

    conn.connect(options);
  });
}

function execSshCommand(conn, command, timeoutMs = 30000) {
  return new Promise((resolve, reject) => {
    let timer = null;

    conn.exec(command, (err, stream) => {
      if (err) {
        reject(err);
        return;
      }

      let stdout = '';
      let stderr = '';

      timer = setTimeout(() => {
        stream.close();
        reject(new Error(`SSH command timed out after ${timeoutMs}ms`));
      }, timeoutMs);

      stream.on('close', (code) => {
        clearTimeout(timer);

        if (code === 0) {
          resolve(`${stdout}${stderr}`);
        } else {
          reject(new Error(stderr || stdout || `SSH command failed with exit code ${code}`));
        }
      });

      stream.on('data', (data) => {
        stdout += data.toString();
      });

      stream.stderr.on('data', (data) => {
        stderr += data.toString();
      });
    });
  });
}

async function installManagedSshKey({ host, port, username, password, privateKey, passphrase }) {
  const key = ensureAppSshKey();

  if (!password && !privateKey) {
    throw new Error('SSH password or private key is required the first time this server installs its managed SSH key on a target host');
  }

  const conn = await connectSsh({
    host,
    port,
    username,
    password,
    privateKey,
    passphrase
  });

  try {
    const publicKey = shellQuote(key.publicKey);

    const command = [
      'umask 077',
      'mkdir -p ~/.ssh',
      'touch ~/.ssh/authorized_keys',
      `grep -qxF ${publicKey} ~/.ssh/authorized_keys || printf "%s\\n" ${publicKey} >> ~/.ssh/authorized_keys`,
      'chmod 700 ~/.ssh',
      'chmod 600 ~/.ssh/authorized_keys'
    ].join(' && ');

    await execSshCommand(conn, command, 30000);
  } finally {
    conn.end();
  }

  return key;
}

async function runManagedSshCommand({ host, port, username, command, password, privateKey, passphrase }) {
  const key = ensureAppSshKey();

  let conn;

  try {
    conn = await connectSsh({
      host,
      port,
      username,
      privateKey: key.privateKey
    });
  } catch (err) {
    if (!password && !privateKey) {
      throw new Error(`Managed SSH key is not trusted by ${username}@${host}. Provide SSH password or private key once so the app can install its public key.`);
    }

    await installManagedSshKey({
      host,
      port,
      username,
      password,
      privateKey,
      passphrase
    });

    conn = await connectSsh({
      host,
      port,
      username,
      privateKey: key.privateKey
    });
  }

  try {
    return await execSshCommand(conn, command, 30000);
  } finally {
    conn.end();
  }
}

function parseTrustToken(output) {
  const text = String(output || '');
  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  const candidates = [];

  for (const line of lines) {
    const matches = line.match(/[A-Za-z0-9._~+/=-]{40,}/g);
    if (matches) candidates.push(...matches);
  }

  const preferred = candidates.find((token) => token.startsWith('eyJ'));
  const token = preferred || candidates[candidates.length - 1];

  if (!token) {
    throw new Error(`Unable to parse trust token from SSH output: ${text}`);
  }

  return token;
}


async function addRemoteViaSsh(options = {}) {
  const name = validateRemoteName(options.name);
  const incusPort = normalizePort(options.incus_port, 8443);
  const sshPort = normalizePort(options.ssh_port, 22);
  const sshUser = String(options.ssh_user || '').trim();
  const trustName = String(options.trust_name || 'IncusMobileServer').trim();

  if (!sshUser) {
    throw new Error('SSH user is required');
  }

  if (!trustName) {
    throw new Error('Trust name is required');
  }

  const { host, incusUrl } = normalizeHost(options.host || options.addr);
  const finalIncusUrl = incusUrl || `https://${host}:${incusPort}`;

  const inventory = await getRemoteInventory();
  if (inventory.managed.some((r) => r.name === name) || inventory.ignored.some((r) => r.name === name)) {
    throw new Error(`Remote "${name}" already exists`);
  }

  const sshAuthMethod = String(options.ssh_auth_method || 'password');
  const sshPassword = String(options.ssh_password || '');
  const sshPrivateKey = String(options.ssh_private_key || '');
  const sshKeyPassphrase = String(options.ssh_key_passphrase || '');

  if (sshAuthMethod === 'key' && !sshPrivateKey) {
    throw new Error('SSH private key is required when SSH auth method is Private Key');
  }

  if (sshAuthMethod !== 'key' && !sshPassword) {
    throw new Error('SSH password is required when SSH auth method is Password');
  }

  const sshOutput = await runManagedSshCommand({
    host,
    port: sshPort,
    username: sshUser,
    password: sshAuthMethod === 'key' ? '' : sshPassword,
    privateKey: sshAuthMethod === 'key' ? sshPrivateKey : '',
    passphrase: sshAuthMethod === 'key' ? sshKeyPassphrase : '',
    command: `incus config trust add ${shellQuote(trustName)}`
  });

  const token = parseTrustToken(sshOutput);

  await runIncus([
    'remote', 'add',
    name,
    finalIncusUrl,
    '--accept-certificate',
    '--token',
    token
  ], 30000);

  const test = await testRemote(name);

  return {
    ok: true,
    name,
    addr: finalIncusUrl,
    test
  };
}

async function removeRemote(name) {
  const safeName = validateRemoteName(name);
  const inventory = await getRemoteInventory();

  const managed = inventory.managed.find((r) => r.name === safeName);
  const ignored = inventory.ignored.find((r) => r.name === safeName);

  if (ignored) {
    throw new Error(`Refusing to remove ignored/reserved remote "${safeName}": ${ignored.reason}`);
  }

  if (!managed) {
    throw new Error(`Managed remote "${safeName}" not found`);
  }

  await runIncus(['remote', 'remove', safeName], 30000);

  return {
    ok: true,
    name: safeName,
    removed: true
  };
}


function getRemoteTcpEndpoint(remote) {
  const rawAddress = String(remote?.addr || '').trim();

  if (!rawAddress) {
    return null;
  }

  try {
    const parsed = new URL(
      rawAddress.includes('://')
        ? rawAddress
        : `https://${rawAddress}`
    );

    return {
      host: parsed.hostname,
      port: Number(parsed.port || 8443)
    };
  } catch {
    /*
     * Fall back to a simple host[:port] parser. IPv6 addresses returned by
     * Incus should normally arrive as proper URLs and use the URL path above.
     */
    const withoutScheme = rawAddress.replace(/^https?:\/\//i, '');
    const hostPort = withoutScheme.split('/')[0];
    const lastColon = hostPort.lastIndexOf(':');

    if (lastColon > 0 && hostPort.indexOf(':') == lastColon) {
      const host = hostPort.slice(0, lastColon);
      const port = Number(hostPort.slice(lastColon + 1));

      return {
        host,
        port: Number.isInteger(port) && port > 0 ? port : 8443
      };
    }

    return {
      host: hostPort,
      port: 8443
    };
  }
}


function probeRemoteTcp(remote, timeoutMs = 2000) {
  return new Promise((resolve) => {
    const endpoint = getRemoteTcpEndpoint(remote);

    if (!endpoint?.host) {
      resolve({
        reachable: null,
        error: 'Remote address is unavailable'
      });
      return;
    }

    let finished = false;

    const socket = net.createConnection({
      host: endpoint.host,
      port: endpoint.port
    });

    const finish = (reachable, error = null) => {
      if (finished) return;
      finished = true;

      socket.destroy();

      resolve({
        reachable,
        host: endpoint.host,
        port: endpoint.port,
        error
      });
    };

    socket.setTimeout(timeoutMs);

    socket.once('connect', () => {
      finish(true);
    });

    socket.once('timeout', () => {
      finish(false, `TCP connection timed out after ${timeoutMs} ms`);
    });

    socket.once('error', (err) => {
      finish(false, err.message);
    });
  });
}


function applyTcpReachabilityToFailure(failure, tcpResult) {
  if (!failure || !tcpResult) {
    return failure;
  }

  /*
   * A successful TCP connection proves that the host and Incus listener are
   * reachable. An inventory timeout therefore cannot correctly mean Offline.
   */
  if (tcpResult.reachable === true) {
    if (failure.reason === 'cluster_no_quorum') {
      return {
        ...failure,
        host_reachable: true,
        inventory_available: false
      };
    }

    if (
      failure.reason === 'host_unreachable' ||
      failure.reason === 'inventory_error'
    ) {
      return {
        ...failure,
        status: 'Inventory Error',
        reason: 'inventory_error',
        host_reachable: true,
        inventory_available: false,
        message: 'The Incus server is reachable, but its inventory could not be retrieved.'
      };
    }
  }

  /*
   * If the Incus TCP listener cannot be reached, classify the remote Offline
   * unless the command already returned an explicit no-quorum response.
   */
  if (
    tcpResult.reachable === false &&
    failure.reason !== 'cluster_no_quorum'
  ) {
    return {
      ...failure,
      status: 'Offline',
      reason: 'host_unreachable',
      host_reachable: false,
      inventory_available: false,
      message: 'The Incus server could not be reached on its configured API port.'
    };
  }

  return failure;
}


function classifyRemoteFailure(error) {
  const details = String(
    error?.message || error || 'Remote inventory query failed'
  );

  const lower = details.toLowerCase();

  if (
    lower.includes('no available cowsql leader') ||
    lower.includes('failed to create cowsql connection') ||
    lower.includes('failed to begin transaction')
  ) {
    return {
      status: 'No Quorum',
      reason: 'cluster_no_quorum',
      host_reachable: true,
      inventory_available: false,
      message: 'This cluster member is reachable, but the Incus cluster does not currently have quorum.',
      details
    };
  }

  if (
    lower.includes('no route to host') ||
    lower.includes('connection refused') ||
    lower.includes('network is unreachable') ||
    lower.includes('name or service not known') ||
    lower.includes('temporary failure in name resolution') ||
    lower.includes('connection timed out') ||
    lower.includes('i/o timeout') ||
    lower.includes('incus command timed out')
  ) {
    return {
      status: 'Offline',
      reason: 'host_unreachable',
      host_reachable: false,
      inventory_available: false,
      message: 'The Incus server could not be reached on its configured API port.',
      details
    };
  }

  return {
    status: 'Inventory Error',
    reason: 'inventory_error',
    host_reachable: null,
    inventory_available: false,
    message: 'The Incus server responded, but its inventory could not be retrieved.',
    details
  };
}

async function testRemote(name) {
  const safeName = validateRemoteName(name);
  const inventory = await getRemoteInventory();
  const remote = inventory.managed.find((item) => item.name === safeName);

  if (!remote) {
    throw new Error(`Managed remote "${safeName}" not found`);
  }

  const tcpProbePromise = probeRemoteTcp(remote);

  try {
    const stdout = await runIncus([
      'list',
      `${safeName}:`,
      '--all-projects',
      '--format',
      'json'
    ], 15000);

    const instances = JSON.parse(stdout);

    return {
      ok: true,
      name: safeName,
      status: 'Online',
      reason: null,
      reachable: true,
      host_reachable: true,
      inventory_available: true,
      instances_count: Array.isArray(instances) ? instances.length : 0
    };
  } catch (err) {
    const tcpResult = await tcpProbePromise;
    const failure = applyTcpReachabilityToFailure(
      classifyRemoteFailure(err),
      tcpResult
    );

    return {
      ok: false,
      name: safeName,
      status: failure.status,
      reason: failure.reason,
      reachable: false,
      host_reachable: failure.host_reachable,
      inventory_available: false,
      instances_count: null,
      tcp_reachable: tcpResult.reachable,
      tcp_host: tcpResult.host || null,
      tcp_port: tcpResult.port || null,
      error: failure.message,
      details: failure.details || null
    };
  }
}


async function getInstancesForRemote(remote) {
  const stdout = await runIncus([
    'list',
    `${remote.name}:`,
    '--all-projects',
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
  const actionsEnabled = getMobileActionsStatus().effective_enabled;

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

  const scans = await Promise.all(
    remotes.map(async (remote) => {
      /*
       * Start the TCP probe immediately so it runs during the inventory
       * request rather than adding another delay after a 15-second timeout.
       */
      const tcpProbePromise = probeRemoteTcp(remote);

      try {
        const instances = await getInstancesForRemote(remote);

        return {
          remote,
          instances,
          error: null,
          tcpResult: await tcpProbePromise
        };
      } catch (err) {
        return {
          remote,
          instances: null,
          error: err,
          tcpResult: await tcpProbePromise
        };
      }
    })
  );

  const results = [];

  scans.forEach((scan) => {
    const remote = scan.remote;

    if (!scan.error) {
      results.push(...scan.instances);
      return;
    }

    const failure = applyTcpReachabilityToFailure(
      classifyRemoteFailure(scan.error),
      scan.tcpResult
    );

    results.push({
      id: `${remote.name}:error`,
      remote: remote.name,
      error: true,
      status: failure.status,
      reason: failure.reason,
      host_reachable: failure.host_reachable,
      inventory_available: false,
      tcp_reachable: scan.tcpResult?.reachable ?? null,
      tcp_host: scan.tcpResult?.host || null,
      tcp_port: scan.tcpResult?.port || null,
      message: failure.message,
      details: failure.details || null
    });
  });

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


async function listInstanceSnapshots(id) {
  const { remote, project, name } = parseInstanceId(id);

  const stdout = await runIncus([
    'snapshot',
    'list',
    `${remote}:${name}`,
    '--project',
    project,
    '--format',
    'json'
  ], 30000);

  try {
    return JSON.parse(stdout || '[]');
  } catch (err) {
    throw new Error(`Unable to parse snapshot list: ${err.message}`);
  }
}

function defaultSnapshotName() {
  return `mobile-${new Date()
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\..+/, '')}`;
}

function validateSnapshotName(name) {
  const value = String(name || '').trim();

  if (!value) {
    return defaultSnapshotName();
  }

  if (!/^[A-Za-z0-9._-]+$/.test(value)) {
    throw new Error('Invalid snapshot name. Use letters, numbers, dots, underscores, and hyphens only.');
  }

  return value;
}

async function createInstanceSnapshot(id, requestedName) {
  const { remote, project, name } = parseInstanceId(id);
  const snapshotName = validateSnapshotName(requestedName);

  await runIncus([
    'snapshot',
    'create',
    `${remote}:${name}`,
    snapshotName,
    '--project',
    project
  ], 60000);

  return {
    ok: true,
    id,
    snapshot: snapshotName
  };
}


module.exports = {
  validateSnapshotName,
  createInstanceSnapshot,
  listInstanceSnapshots,
  getRemotes,
  getAllInstances,
  runInstanceAction,
  parseInstanceId,
  getRemoteInventory,
  addRemoteViaSsh,
  removeRemote,
  testRemote
};
