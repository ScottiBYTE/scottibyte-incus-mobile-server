let state = {
  health: null,
  summary: null,
  remotes: [],
  instances: [],
  clients: [],
  operations: [],
  operationsPreview: [],
  ignoredRemotes: [],
  remoteTests: {},
  instanceSort: { key: 'remote', dir: 'asc' },
  clientSort: { key: 'status', dir: 'asc' },
  remoteSort: { key: 'name', dir: 'asc' }
};

function $(id) {
  return document.getElementById(id);
}

async function fetchJson(url, options = {}) {
  const res = await fetch(url, options);
  const data = await res.json().catch(() => ({}));
  if (!res.ok || data.ok === false) {
    throw new Error(data.error || `${res.status} ${res.statusText}`);
  }
  return data;
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function normalizeRemotes(data) {
  if (!data) return [];
  if (Array.isArray(data.managed)) return data.managed;
  if (Array.isArray(data.remotes)) {
    return data.remotes.map((r) => typeof r === 'string' ? { name: r } : r);
  }
  if (typeof data.remotes === 'object') {
    return Object.entries(data.remotes).map(([name, info]) => ({ name, ...info }));
  }
  return [];
}

function getValue(row, key) {
  switch (key) {
    case 'memory_display':
      return row.memory?.display || '';
    case 'disk_display':
      return row.disk?.display || '';
    case 'backups_count':
      return row.backups?.count ?? 0;
    case 'snapshots_count':
      return row.snapshots?.count ?? 0;
    case 'cpu_percent':
      return row.cpu?.percent ?? -1;
    default:
      return row[key] ?? '';
  }
}

function compareValues(a, b) {
  const na = Number(a);
  const nb = Number(b);

  if (!Number.isNaN(na) && !Number.isNaN(nb) && String(a).trim() !== '' && String(b).trim() !== '') {
    return na - nb;
  }

  return String(a ?? '').localeCompare(String(b ?? ''), undefined, {
    numeric: true,
    sensitivity: 'base'
  });
}

function sortRows(rows, sort) {
  return [...rows].sort((a, b) => {
    const result = compareValues(getValue(a, sort.key), getValue(b, sort.key));
    return sort.dir === 'asc' ? result : -result;
  });
}

function badge(text) {
  const cls = String(text || '').toLowerCase().replaceAll(' ', '-');
  return `<span class="badge ${cls}">${escapeHtml(text || '-')}</span>`;
}

function bubble(value, type = 'neutral') {
  return `<span class="bubble ${escapeHtml(type)}">${escapeHtml(value || '-')}</span>`;
}

function statusBubble(status) {
  const value = String(status || '-');
  if (value === 'Running' || value === 'approved' || value === 'Online') return bubble(value, 'good');
  if (value === 'Stopped' || value === 'revoked' || value === 'Offline') return bubble(value, 'bad');
  if (value === 'pending' || value === 'Not tested') return bubble(value, 'warn');
  return bubble(value, 'neutral');
}

function roleBubble(role) {
  if (role === 'operator') return bubble(role, 'purple');
  if (role === 'viewer') return bubble(role, 'remote');
  return bubble(role || '-', 'neutral');
}

function metricBubble(value) {
  const raw = String(value || '-');
  if (raw === '-' || raw === '0 B') return bubble(raw, 'neutral');
  if (raw.includes('GB')) return bubble(raw, 'warn');
  return bubble(raw, 'good');
}


function renderStatus() {
  const actionsEl = $('actionsStatus');
  actionsEl.textContent = state.health?.actions_enabled ? 'Enabled' : 'Disabled';
  actionsEl.classList.remove('enabled', 'disabled');
  actionsEl.classList.add(state.health?.actions_enabled ? 'enabled' : 'disabled');

  $('remoteCount').textContent = String(state.remotes.length);

  const approved = state.clients.filter((c) => c.status === 'approved').length;
  const pending = state.clients.filter((c) => c.status === 'pending').length;
  $('approvedClientCount').textContent = String(approved);
  $('pendingClientCount').textContent = String(pending);
}

function renderSummary() {
  const s = state.summary || {};
  $('instancesTotal').textContent = s.instances_total ?? '-';
  $('runningTotal').textContent = s.running ?? '-';
  $('stoppedTotal').textContent = s.stopped ?? '-';
  $('containersTotal').textContent = s.containers_total ?? '-';
  $('vmsTotal').textContent = s.virtual_machines_total ?? '-';
  const errorsEl = $('errorsTotal');
  errorsEl.textContent = s.errors ?? '-';
  errorsEl.classList.toggle('has-errors', Number(s.errors || 0) > 0);

  const summaryCards = $('summaryCards');
  if (summaryCards) {
    const errorCard = summaryCards.children[5];
    if (errorCard) errorCard.classList.toggle('has-errors', Number(s.errors || 0) > 0);
  }
}

function renderRemotes() {
  const rows = sortRows(state.remotes, state.remoteSort);
  const tbody = $('remotesBody');

  const remoteRows = rows.map((r) => {
    const test = state.remoteTests[r.name];
    const status = test
      ? (test.reachable ? statusBubble('Online') : statusBubble('Offline'))
      : statusBubble('Not tested');

    const count = test && test.reachable ? test.instances_count : '-';

    return `
      <tr>
        <td>${bubble(r.name, 'remote')}</td>
        <td>${escapeHtml(r.addr || '-')}</td>
        <td>${bubble(r.protocol || '-', 'neutral')}</td>
        <td>${bubble(r.auth_type || '-', 'neutral')}</td>
        <td>${escapeHtml(r.project || 'default')}</td>
        <td>${status}</td>
        <td>${escapeHtml(count)}</td>
        <td>
          <div class="actions">
            <button class="btn" onclick="testRemote('${escapeHtml(r.name)}')">Test</button>
            <button class="btn danger" onclick="deleteRemote('${escapeHtml(r.name)}')">Delete</button>
          </div>
        </td>
      </tr>
    `;
  }).join('');

  tbody.innerHTML = remoteRows;

  const addBtn = $('addRemoteBtn');
  if (addBtn) addBtn.addEventListener('click', addRemote);

  renderIgnoredRemotes();
  updateSortIndicators('remotesTable', state.remoteSort);
  reapplyTableSelection('remotesTable');
}

function renderIgnoredRemotes() {
  const el = $('ignoredRemotesList');
  if (!el) return;

  if (!state.ignoredRemotes.length) {
    el.innerHTML = '<div>No ignored remotes.</div>';
    return;
  }

  el.innerHTML = state.ignoredRemotes.map((r) => `
    <div>
      <strong>${escapeHtml(r.name)}</strong>
      — ${escapeHtml(r.addr || '-')}
      — ${escapeHtml(r.protocol || '-')}
      — ${escapeHtml(r.reason || 'Ignored')}
    </div>
  `).join('');
}

function renderRemoteFilter() {
  const select = $('remoteFilter');
  const current = select.value;
  const remotes = [...new Set(state.instances.map((i) => i.remote).filter(Boolean))].sort();

  select.innerHTML = '<option value="">All remotes</option>' +
    remotes.map((r) => `<option value="${escapeHtml(r)}">${escapeHtml(r)}</option>`).join('');

  if (remotes.includes(current)) select.value = current;
}

function filteredInstances() {
  const q = $('searchInput').value.trim().toLowerCase();
  const remote = $('remoteFilter').value;
  const status = $('statusFilter').value;
  const type = $('typeFilter').value;

  return state.instances.filter((i) => {
    if (remote && i.remote !== remote) return false;
    if (status && i.status !== status) return false;
    if (type && i.type !== type) return false;

    if (!q) return true;

    const haystack = [
      i.id, i.remote, i.name, i.type, i.status, i.primary_ipv4,
      i.memory?.display, i.disk?.display
    ].join(' ').toLowerCase();

    return haystack.includes(q);
  });
}

function renderInstances() {
  const rows = sortRows(filteredInstances(), state.instanceSort);

  $('instancesBody').innerHTML = rows.map((i) => `
    <tr>
      <td>${bubble(i.remote, 'remote')}</td>
      <td>${escapeHtml(i.name)}</td>
      <td>${bubble(i.type, i.type === 'virtual-machine' ? 'purple' : 'neutral')}</td>
      <td>${statusBubble(i.status)}</td>
      <td>${i.primary_ipv4 ? bubble(i.primary_ipv4, 'ip') : '-'}</td>
      <td>${i.cpu?.percent == null ? '-' : bubble(i.cpu.percent, 'good')}</td>
      <td>${metricBubble(i.memory?.display || '-')}</td>
      <td>${metricBubble(i.disk?.display || '-')}</td>
      <td>${escapeHtml(i.backups?.count ?? 0)}</td>
      <td>${escapeHtml(i.snapshots?.count ?? 0)}</td>
    </tr>
  `).join('');

  updateSortIndicators('instancesTable', state.instanceSort);
  reapplyTableSelection('instancesTable');
}


function renderAudit(events) {
  const body = $('auditBody');

  if (!body) return;

  if (!events || events.length === 0) {
    body.innerHTML = `
      <tr>
        <td colspan="6" class="muted">No audit events yet.</td>
      </tr>
    `;
    return;
  }

  body.innerHTML = events.map((event) => {
    const actor = event.actor_name || event.actor_type || '-';
    const meta = event.metadata || {};
    const target =
      meta.new_display_name ||
      meta.display_name ||
      meta.device_name ||
      meta.device_id ||
      event.target_id ||
      event.target_type ||
      '-';
    const resultType = event.result === 'success'
      ? 'good'
      : event.result === 'failed'
        ? 'bad'
        : 'neutral';

    return `
      <tr>
        <td>${escapeHtml(event.created_at || '-')}</td>
        <td>${bubble(actor, 'remote')}</td>
        <td>${escapeHtml(event.event_type || '-')}</td>
        <td>${escapeHtml(target)}</td>
        <td>${bubble(event.result || '-', resultType)}</td>
        <td>${escapeHtml(event.message || '-')}</td>
      </tr>
    `;
  }).join('');
}

async function loadAudit() {
  const data = await fetchJson('/api/admin/audit-events?limit=25');
  renderAudit(data.events || []);
}



function renderOperationsPreview() {
  const el = $('operationsPreview');

  if (!el) return;

  if (!state.operationsPreview || state.operationsPreview.length === 0) {
    el.innerHTML = '<span class="muted">No discovery preview available.</span>';
    return;
  }

  el.innerHTML = `
    <div class="operations-preview-heading">
      <strong>Mobile Action Discovery Preview</strong>
      <span class="note">Shows which enabled operations each mobile role can request. Viewer clients still have read-only inventory access.</span>
    </div>
    <div class="operations-preview-grid">
      ${state.operationsPreview.map((entry) => {
        const operations = entry.operations || [];
        const names = operations.map((op) => op.operation);

        return `
          <div class="operations-preview-card">
            <div class="operations-preview-role">${escapeHtml(entry.role)}</div>
            <div class="operations-preview-list">
              ${
                names.length
                  ? names.map((name) => bubble(name, 'remote')).join(' ')
                  : `${bubble('Read-only access', 'remote')}`
              }
            </div>
          </div>
        `;
      }).join('')}
    </div>
  `;
}

async function loadOperationsPreview() {
  const data = await fetchJson('/api/admin/operations-preview');
  state.operationsPreview = data.preview || [];
  renderOperationsPreview();
}


function renderDryRunOperationOptions() {
  const select = $('dryRunOperation');

  if (!select) return;

  const current = select.value;
  const operations = state.operations || [];

  select.innerHTML = operations.map((op) => `
    <option value="${escapeHtml(op.operation_key)}">${escapeHtml(op.operation_key)}</option>
  `).join('');

  if (operations.some((op) => op.operation_key === current)) {
    select.value = current;
  }
}

function formatDryRunResult(result) {
  const dryRun = result?.dry_run || result;

  if (!dryRun) {
    return 'No dry run result.';
  }

  const lines = [];

  lines.push(`Allowed: ${dryRun.allowed ? 'yes' : 'no'}`);
  lines.push(`Reason: ${dryRun.reason || '-'}`);
  lines.push(`Operation: ${dryRun.operation || '-'}`);
  lines.push(`Target: ${dryRun.target_id || '-'}`);

  if (dryRun.role) {
    lines.push(`Simulated role: ${dryRun.role}`);
  }

  if (dryRun.role_required) {
    lines.push(`Role required: ${dryRun.role_required}`);
  }

  if (Array.isArray(dryRun.argv)) {
    lines.push('');
    lines.push('Incus argv:');
    lines.push(`incus ${dryRun.argv.map((v) => String(v).includes(' ') ? JSON.stringify(v) : v).join(' ')}`);
  }

  return lines.join('\n');
}

async function runOperationDryRun() {
  const btn = $('dryRunBtn');
  const resultEl = $('dryRunResult');

  const payload = {
    operation: $('dryRunOperation').value,
    target_type: $('dryRunTargetType').value.trim() || 'instance',
    target_id: $('dryRunTargetId').value.trim(),
    role: $('dryRunRole').value,
    params: {}
  };

  if (!payload.operation || !payload.target_id) {
    alert('Operation and Target ID are required.');
    return;
  }

  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Testing...';
  }

  if (resultEl) {
    resultEl.classList.remove('good', 'bad');
    resultEl.textContent = 'Running dry run...';
  }

  try {
    const data = await fetchJson('/api/admin/operations/dry-run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const dryRun = data.dry_run || {};
    if (resultEl) {
      resultEl.textContent = formatDryRunResult(data);
      resultEl.classList.toggle('good', Boolean(dryRun.allowed));
      resultEl.classList.toggle('bad', !dryRun.allowed);
    }

    await loadAudit();
  } catch (err) {
    if (resultEl) {
      resultEl.textContent = err.message || 'Dry run failed';
      resultEl.classList.add('bad');
    }

    alert(err.message);
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.textContent = 'Dry Run';
    }
  }
}

function renderOperations() {
  const body = $('operationsBody');

  if (!body) return;

  if (!state.operations || state.operations.length === 0) {
    body.innerHTML = `
      <tr>
        <td colspan="8" class="muted">No operation definitions found.</td>
      </tr>
    `;
    return;
  }

  renderDryRunOperationOptions();

  body.innerHTML = state.operations.map((op) => {
    const enabledText = op.enabled ? 'Enabled' : 'Disabled';
    const enabledType = op.enabled ? 'good' : 'bad';
    const nextEnabled = op.enabled ? 'false' : 'true';
    const nextLabel = op.enabled ? 'Disable' : 'Enable';
    return `
      <tr>
        <td>${bubble(op.operation_key, 'remote')}</td>
        <td>
          <strong>${escapeHtml(op.label || op.operation_key)}</strong><br>
          <span class="note">${escapeHtml(op.description || '')}</span>
        </td>
        <td>${bubble(enabledText, enabledType)}</td>
        <td>
          <select onchange="changeOperationRole('${escapeHtml(op.operation_key)}', this.value)">
            <option value="operator" ${op.role_required === 'operator' ? 'selected' : ''}>operator</option>
            <option value="admin" ${op.role_required === 'admin' ? 'selected' : ''}>admin</option>
          </select>
        </td>
        <td>${bubble(op.target_type || '-', 'neutral')}</td>
        <td>
          <div class="actions">
            <button class="btn ${op.enabled ? 'danger' : 'primary'}" onclick="setOperationEnabled('${escapeHtml(op.operation_key)}', ${nextEnabled})">${nextLabel}</button>
          </div>
        </td>
      </tr>
    `;
  }).join('');

  reapplyTableSelection('operationsTable');
}

async function loadOperations() {
  const data = await fetchJson('/api/admin/operations');
  state.operations = data.operations || [];
  renderOperations();
}

async function setOperationEnabled(operationKey, enabled) {
  try {
    await fetchJson(`/api/admin/operations/${encodeURIComponent(operationKey)}/enabled`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled })
    });

    await loadOperations();
    await loadOperationsPreview();
    await loadAudit();
  } catch (err) {
    alert(err.message);
  }
}

async function changeOperationRole(operationKey, roleRequired) {
  try {
    await fetchJson(`/api/admin/operations/${encodeURIComponent(operationKey)}/role`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role_required: roleRequired })
    });

    await loadOperations();
    await loadOperationsPreview();
    await loadAudit();
  } catch (err) {
    alert(err.message);
    await loadOperations();
  }
}

function renderClients() {
  const rows = sortRows(state.clients, state.clientSort);

  $('clientsBody').innerHTML = rows.map((c) => `
    <tr>
      <td>
        <strong>${escapeHtml(c.display_name || c.device_name || c.device_id)}</strong><br>
        <span class="note">
          ${escapeHtml(c.device_id)}
          ${c.display_name && c.device_name ? `<br>reported as: ${escapeHtml(c.device_name)}` : ''}
        </span>
      </td>
      <td>${statusBubble(c.status)}</td>
      <td>${roleBubble(c.role)}</td>
      <td>${escapeHtml(c.last_seen_at || '-')}</td>
      <td>
        <div class="actions">
          <button class="btn" onclick="renameClient(${c.id}, '${escapeHtml(c.display_name || c.device_name || '')}')">Rename</button>
          ${c.status === 'pending' ? `
            <button class="btn" onclick="approveClient(${c.id}, 'viewer')">Approve Viewer</button>
            <button class="btn primary" onclick="approveClient(${c.id}, 'operator')">Approve Operator</button>
          ` : ''}
          ${c.status === 'approved' ? `
            <button class="btn danger" onclick="revokeClient(${c.id})">Revoke</button>
          ` : ''}
        </div>
      </td>
    </tr>
  `).join('');

  updateSortIndicators('clientsTable', state.clientSort);
  reapplyTableSelection('clientsTable');
}

function updateSortIndicators(tableId, sort) {
  document.querySelectorAll(`#${tableId} thead th`).forEach((th) => {
    const key = th.dataset.sort;
    th.querySelectorAll('.sort-indicator').forEach((el) => el.remove());

    if (key && key === sort.key) {
      const span = document.createElement('span');
      span.className = 'sort-indicator';
      span.textContent = sort.dir === 'asc' ? '▲' : '▼';
      th.appendChild(span);
    }
  });
}

function attachSortHandlers() {
  document.querySelectorAll('#instancesTable th[data-sort]').forEach((th) => {
    th.addEventListener('click', () => {
      const key = th.dataset.sort;
      state.instanceSort = {
        key,
        dir: state.instanceSort.key === key && state.instanceSort.dir === 'asc' ? 'desc' : 'asc'
      };
      renderInstances();
    });
  });

  document.querySelectorAll('#clientsTable th[data-sort]').forEach((th) => {
    th.addEventListener('click', () => {
      const key = th.dataset.sort;
      state.clientSort = {
        key,
        dir: state.clientSort.key === key && state.clientSort.dir === 'asc' ? 'desc' : 'asc'
      };
      renderClients();
    });
  });

  document.querySelectorAll('#remotesTable th[data-sort]').forEach((th) => {
    th.addEventListener('click', () => {
      const key = th.dataset.sort;
      state.remoteSort = {
        key,
        dir: state.remoteSort.key === key && state.remoteSort.dir === 'asc' ? 'desc' : 'asc'
      };
      renderRemotes();
    });
  });
}

async function addRemote() {
  const payload = {
    name: $('addRemoteName').value.trim(),
    host: $('addRemoteHost').value.trim(),
    incus_port: $('addRemoteIncusPort').value.trim() || '8443',
    ssh_user: $('addRemoteSshUser').value.trim(),
    ssh_port: $('addRemoteSshPort').value.trim() || '22',
    ssh_password: $('addRemoteSshPassword').value,
    trust_name: $('addRemoteTrustName').value.trim() || 'IncusMobileServer'
  };

  if (!payload.name || !payload.host || !payload.ssh_user) {
    alert('Remote name, Incus host/address, and SSH user are required.');
    return;
  }

  const btn = $('addRemoteBtn');
  btn.disabled = true;
  btn.textContent = 'Adding...';

  try {
    await fetchJson('/api/admin/remotes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    await loadData();
  } catch (err) {
    alert(err.message);
  } finally {
    $('addRemoteSshPassword').value = '';
    btn.disabled = false;
    btn.textContent = 'Add Remote';
  }
}

async function testRemote(name) {
  try {
    const data = await fetchJson(`/api/admin/remotes/${encodeURIComponent(name)}/test`, {
      method: 'POST'
    });

    state.remoteTests[name] = data.test;
    renderRemotes();

    if (typeof loadAudit === 'function') {
      await loadAudit();
    }
  } catch (err) {
    alert(err.message);
  }
}

async function deleteRemote(name) {
  const message = [
    `Delete Incus remote "${name}" from this server?`,
    '',
    'This removes the remote from the Incus client configuration used by this app.',
    'It does not delete containers, VMs, or the remote Incus server.',
    '',
    `Type the remote name to confirm: ${name}`
  ].join("\n");

  const confirmation = prompt(message);

  if (confirmation !== name) {
    return;
  }

  try {
    await fetchJson(`/api/admin/remotes/${encodeURIComponent(name)}`, {
      method: 'DELETE'
    });

    delete state.remoteTests[name];
    await loadData();
  } catch (err) {
    alert(err.message);
  }
}

async function renameClient(id, currentName) {
  const nextName = prompt('Enter admin display name for this client. Leave blank to clear custom name.', currentName || '');

  if (nextName === null) {
    return;
  }

  await fetchJson(`/api/admin/clients/${id}/rename`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ display_name: nextName.trim() })
  });

  await loadData();
}

async function approveClient(id, role) {
  await fetchJson(`/api/admin/clients/${id}/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role })
  });
  await loadData();
}

async function revokeClient(id) {
  await fetchJson(`/api/admin/clients/${id}/revoke`, { method: 'POST' });
  await loadData();
}

async function loadData() {
  try {
    const [health, summaryData, remotesData, instancesData, clientsData, operationsData, operationsPreviewData] = await Promise.all([
      fetchJson('/api/mobile/health'),
      fetchJson('/api/mobile/summary'),
      fetchJson('/api/admin/remotes'),
      fetchJson('/api/mobile/instances'),
      fetchJson('/api/admin/clients'),
      fetchJson('/api/admin/operations'),
      fetchJson('/api/admin/operations-preview')
    ]);

    state.health = health;
    state.summary = summaryData.summary || {};
    state.remotes = normalizeRemotes(remotesData);
    state.ignoredRemotes = remotesData.ignored || [];
    state.instances = instancesData.instances || [];
    state.clients = clientsData.clients || [];
    state.operations = operationsData.operations || [];
    state.operationsPreview = operationsPreviewData.preview || [];

    renderStatus();
    renderSummary();
    renderRemotes();
    renderRemoteFilter();
    renderClients();
    renderOperations();
    renderOperationsPreview();
    renderInstances();
    await loadAudit();
  } catch (err) {
    console.error(err);
    alert(err.message);
  }
}


const selectedRows = {};

function getSelectableRows(tableId) {
  return Array.from(document.querySelectorAll(`#${tableId} tbody tr`))
    .filter((row) => !row.classList.contains('add-row'));
}

function selectTableRow(tableId, index) {
  const rows = getSelectableRows(tableId);
  if (!rows.length) return;

  const safeIndex = Math.max(0, Math.min(index, rows.length - 1));
  selectedRows[tableId] = safeIndex;

  rows.forEach((row, i) => {
    row.classList.toggle('selected-row', i === safeIndex);
  });

  rows[safeIndex].scrollIntoView({
    block: 'nearest',
    inline: 'nearest'
  });
}

function clearTableSelection(tableId) {
  selectedRows[tableId] = null;
  getSelectableRows(tableId).forEach((row) => row.classList.remove('selected-row'));
}

function reapplyTableSelection(tableId) {
  const rows = getSelectableRows(tableId);
  const current = selectedRows[tableId];

  if (current === null || current === undefined || !rows.length) return;

  selectTableRow(tableId, Math.min(current, rows.length - 1));
}

function attachTableSelection() {
  document.querySelectorAll('.selectable-table').forEach((wrap) => {
    const tableId = wrap.dataset.table;
    if (!tableId) return;

    wrap.addEventListener('click', (event) => {
      const row = event.target.closest('tbody tr');
      if (!row || row.classList.contains('add-row')) return;

      const rows = getSelectableRows(tableId);
      const index = rows.indexOf(row);
      if (index >= 0) {
        selectTableRow(tableId, index);
        wrap.focus();
      }
    });

    wrap.addEventListener('keydown', (event) => {
      const rows = getSelectableRows(tableId);
      if (!rows.length) return;

      const current = selectedRows[tableId] ?? 0;

      if (event.key === 'ArrowDown') {
        event.preventDefault();
        selectTableRow(tableId, current + 1);
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        selectTableRow(tableId, current - 1);
      } else if (event.key === 'Home') {
        event.preventDefault();
        selectTableRow(tableId, 0);
      } else if (event.key === 'End') {
        event.preventDefault();
        selectTableRow(tableId, rows.length - 1);
      } else if (event.key === 'Escape') {
        event.preventDefault();
        clearTableSelection(tableId);
      }
    });
  });
}


function applyTheme(theme) {
  const effective = theme === 'light' ? 'light' : 'dark';
  document.body.classList.toggle('light-theme', effective === 'light');

  const btn = $('themeToggleBtn');
  if (btn) {
    btn.textContent = effective === 'light' ? '🌙 Dark' : '☀ Light';
  }

  localStorage.setItem('scottibyteIncusMobileTheme', effective);
}

function toggleTheme() {
  const current = localStorage.getItem('scottibyteIncusMobileTheme') || 'dark';
  applyTheme(current === 'light' ? 'dark' : 'light');
}

function initTheme() {
  applyTheme(localStorage.getItem('scottibyteIncusMobileTheme') || 'dark');
}




async function refreshAuditWithFeedback() {
  const btn = $('refreshAuditBtn');

  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Refreshing...';
  }

  try {
    await loadAudit();

    if (btn) {
      btn.textContent = 'Updated';
      setTimeout(() => {
        btn.textContent = 'Refresh Activity';
        btn.disabled = false;
      }, 1200);
    }
  } catch (err) {
    if (btn) {
      btn.textContent = 'Refresh Failed';
      setTimeout(() => {
        btn.textContent = 'Refresh Activity';
        btn.disabled = false;
      }, 1800);
    }

    alert(err.message || 'Audit refresh failed');
  }
}

async function refreshRemotesOnly() {
  const btn = $('refreshRemotesBtn');

  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Refreshing...';
  }

  try {
    await loadData();

    if (btn) {
      btn.textContent = 'Updated';
      setTimeout(() => {
        btn.textContent = 'Refresh Remotes';
        btn.disabled = false;
      }, 1200);
    }
  } catch (err) {
    if (btn) {
      btn.textContent = 'Refresh Failed';
      setTimeout(() => {
        btn.textContent = 'Refresh Remotes';
        btn.disabled = false;
      }, 1800);
    }

    alert(err.message || 'Refresh failed');
  }
}

async function logoutAdmin() {
  try {
    await fetch('/api/admin/auth/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    });
  } catch (err) {
    // Even if the request fails, send the browser back to login.
  }

  window.location.href = '/admin/login';
}

function init() {
  initTheme();

  $('themeToggleBtn').addEventListener('click', toggleTheme);
  $('logoutBtn').addEventListener('click', logoutAdmin);
  $('refreshRemotesBtn').addEventListener('click', refreshRemotesOnly);
  $('refreshAuditBtn').addEventListener('click', refreshAuditWithFeedback);

  ['searchInput', 'remoteFilter', 'statusFilter', 'typeFilter'].forEach((id) => {
    $(id).addEventListener('input', renderInstances);
    $(id).addEventListener('change', renderInstances);
  });

  attachSortHandlers();
  attachTableSelection();
  loadData();
}

document.addEventListener('DOMContentLoaded', init);
