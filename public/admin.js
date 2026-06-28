let state = {
  health: null,
  summary: null,
  remotes: [],
  instances: [],
  clients: [],
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
            <button class="btn" onclick="approveClient(${c.id}, 'viewer')">Viewer</button>
            <button class="btn primary" onclick="approveClient(${c.id}, 'operator')">Operator</button>
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
  } catch (err) {
    state.remoteTests[name] = {
      reachable: false,
      error: err.message
    };
    renderRemotes();
  }
}

async function deleteRemote(name) {
  if (!confirm(`Remove Incus remote "${name}" from this server?`)) {
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
    const [health, summaryData, remotesData, instancesData, clientsData] = await Promise.all([
      fetchJson('/api/mobile/health'),
      fetchJson('/api/mobile/summary'),
      fetchJson('/api/admin/remotes'),
      fetchJson('/api/mobile/instances'),
      fetchJson('/api/admin/clients')
    ]);

    state.health = health;
    state.summary = summaryData.summary || {};
    state.remotes = normalizeRemotes(remotesData);
    state.ignoredRemotes = remotesData.ignored || [];
    state.instances = instancesData.instances || [];
    state.clients = clientsData.clients || [];

    renderStatus();
    renderSummary();
    renderRemotes();
    renderRemoteFilter();
    renderClients();
    renderInstances();
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

  ['searchInput', 'remoteFilter', 'statusFilter', 'typeFilter'].forEach((id) => {
    $(id).addEventListener('input', renderInstances);
    $(id).addEventListener('change', renderInstances);
  });

  attachSortHandlers();
  attachTableSelection();
  loadData();
}

document.addEventListener('DOMContentLoaded', init);
