let allInstances = [];

async function fetchJson(url) {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`${url} returned HTTP ${res.status}`);
  }
  return res.json();
}

function setText(id, value) {
  document.getElementById(id).textContent = value;
}

function badge(text, cls) {
  return `<span class="badge ${cls}">${escapeHtml(text)}</span>`;
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function renderSummary(summary, remoteCount) {
  setText('instancesTotal', summary.instances_total ?? '-');
  setText('runningTotal', summary.running ?? '-');
  setText('notRunningTotal', summary.not_running ?? '-');
  setText('remoteTotal', remoteCount ?? '-');
}

function renderRemotes(remotes) {
  const el = document.getElementById('remoteList');

  if (!remotes.length) {
    el.textContent = 'No remotes found.';
    return;
  }

  el.innerHTML = remotes
    .map(r => `<div class="remote-pill">${escapeHtml(r.name)} · ${escapeHtml(r.project)}</div>`)
    .join('');
}

function renderInstances() {
  const tbody = document.getElementById('instanceRows');
  const search = document.getElementById('searchBox').value.toLowerCase().trim();

  const rows = allInstances.filter(i => {
    if (!search) return true;

    const haystack = [
      i.remote,
      i.project,
      i.name,
      i.type,
      i.status,
      i.primary_ipv4,
      i.mac,
      i.memory?.display,
      i.disk?.display
    ].join(' ').toLowerCase();

    return haystack.includes(search);
  });

  if (!rows.length) {
    tbody.innerHTML = '<tr><td colspan="10">No matching instances.</td></tr>';
    return;
  }

  tbody.innerHTML = rows.map(i => {
    const typeClass = i.type === 'virtual-machine' ? 'vm' : 'container';
    const statusClass = i.status === 'Running' ? 'running' : 'stopped';

    return `
      <tr>
        <td>${badge(i.remote, 'host')}</td>
        <td><strong>${escapeHtml(i.name)}</strong></td>
        <td>${badge(i.type === 'virtual-machine' ? 'VM' : 'Container', typeClass)}</td>
        <td>${badge(i.status, statusClass)}</td>
        <td>${escapeHtml(i.primary_ipv4 || '')}</td>
        <td>${escapeHtml(i.mac || '')}</td>
        <td>${escapeHtml(i.memory?.display || '')}</td>
        <td>${escapeHtml(i.disk?.display || '')}</td>
        <td>${escapeHtml(i.backups?.count ?? 0)}</td>
        <td>${escapeHtml(i.snapshots?.count ?? 0)}</td>
      </tr>
    `;
  }).join('');
}

async function loadAll() {
  const refreshBtn = document.getElementById('refreshBtn');
  refreshBtn.disabled = true;
  refreshBtn.textContent = 'Refreshing...';

  try {
    const [summaryData, remoteData, instanceData] = await Promise.all([
      fetchJson('/api/mobile/summary'),
      fetchJson('/api/mobile/remotes'),
      fetchJson('/api/mobile/instances')
    ]);

    renderSummary(summaryData.summary, remoteData.remotes.length);
    renderRemotes(remoteData.remotes);
    allInstances = instanceData.instances || [];
    renderInstances();
  } catch (err) {
    alert(err.message);
  } finally {
    refreshBtn.disabled = false;
    refreshBtn.textContent = 'Refresh';
  }
}

document.getElementById('refreshBtn').addEventListener('click', loadAll);
document.getElementById('searchBox').addEventListener('input', renderInstances);

loadAll();

async function loadClients() {
  const tbody = document.getElementById('clientRows');

  if (!tbody) return;

  try {
    const data = await fetchJson('/api/admin/clients');
    const clients = data.clients || [];

    if (!clients.length) {
      tbody.innerHTML = '<tr><td colspan="8">No mobile clients yet.</td></tr>';
      return;
    }

    tbody.innerHTML = clients.map(c => {
      const statusClass = c.status || 'pending';

      let actions = '';

      if (c.status === 'pending') {
        actions = `
          <div class="action-row">
            <button class="small-btn viewer-btn" onclick="approveClient(${c.id}, 'viewer')">Approve Viewer</button>
            <button class="small-btn operator-btn" onclick="approveClient(${c.id}, 'operator')">Approve Operator</button>
            <button class="small-btn danger-btn" onclick="revokeClient(${c.id})">Reject</button>
          </div>
        `;
      } else if (c.status === 'approved') {
        actions = `
          <div class="action-row">
            <button class="small-btn danger-btn" onclick="revokeClient(${c.id})">Revoke</button>
          </div>
        `;
      } else {
        actions = '<span class="muted">No actions</span>';
      }

      return `
        <tr>
          <td><strong>${escapeHtml(c.device_name)}</strong><br><span class="muted">${escapeHtml(c.device_id)}</span></td>
          <td>${badge(c.status, statusClass)}</td>
          <td>${escapeHtml(c.role)}</td>
          <td>${escapeHtml(c.app_version || '')}</td>
          <td>${escapeHtml(c.created_at || '')}</td>
          <td>${escapeHtml(c.last_seen_at || '')}</td>
          <td>${escapeHtml(c.last_ip || '')}</td>
          <td>${actions}</td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="8">${escapeHtml(err.message)}</td></tr>`;
  }
}

async function approveClient(id, role) {
  const res = await fetch(`/api/admin/clients/${id}/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role })
  });

  const data = await res.json();

  if (!data.ok) {
    alert(data.error || 'Failed to approve client');
    return;
  }

  const tokenBox = document.getElementById('tokenBox');
  tokenBox.classList.remove('hidden');
  tokenBox.innerHTML = `
    <strong>Device approved.</strong><br>
    Role: ${escapeHtml(data.role)}<br><br>
    <span class="muted">The phone will receive its token the next time it checks pairing status.</span>
  `;

  await loadClients();
}

async function revokeClient(id) {
  if (!confirm('Revoke this mobile client?')) return;

  const res = await fetch(`/api/admin/clients/${id}/revoke`, {
    method: 'POST'
  });

  const data = await res.json();

  if (!data.ok) {
    alert(data.error || 'Failed to revoke client');
    return;
  }

  await loadClients();
}

document.getElementById('reloadClientsBtn')?.addEventListener('click', loadClients);

const originalLoadAll = loadAll;
loadAll = async function() {
  await originalLoadAll();
  await loadClients();
};

loadClients();
