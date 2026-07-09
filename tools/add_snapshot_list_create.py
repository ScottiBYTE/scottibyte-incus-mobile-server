#!/usr/bin/env python3

from pathlib import Path
from datetime import datetime
import re
import sys

ROOT = Path.cwd()
INCUS = ROOT / "incus.js"
MOBILE = ROOT / "routes" / "mobile.js"

STAMP = datetime.now().strftime("%Y%m%d-%H%M%S")
BACKUP_DIR = ROOT / f"backups/snapshot-list-create-{STAMP}"

INCUS_HELPERS = r'''

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
'''

MOBILE_ROUTES = r'''

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
'''

def backup():
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    for path in [INCUS, MOBILE]:
        if not path.exists():
            raise SystemExit(f"Missing expected file: {path}")
        target = BACKUP_DIR / path.name
        target.write_text(path.read_text())
    print(f"Backup created: {BACKUP_DIR}")

def patch_incus():
    text = INCUS.read_text()

    if "async function listInstanceSnapshots" not in text:
        marker = "\n\nmodule.exports = {"
        if marker not in text:
            raise SystemExit("Could not find module.exports marker in incus.js")

        text = text.replace(marker, INCUS_HELPERS + marker)

    exports = [
        "listInstanceSnapshots",
        "createInstanceSnapshot",
        "validateSnapshotName",
    ]

    for name in exports:
        if re.search(rf"\b{name}\b", text.split("module.exports = {", 1)[1]):
            continue

        text = text.replace(
            "module.exports = {\n",
            f"module.exports = {{\n  {name},\n",
            1
        )

    INCUS.write_text(text)
    print("Patched incus.js")

def patch_mobile():
    text = MOBILE.read_text()

    old_import = "const { getAllInstances, getRemotes, runInstanceAction } = require('../incus');"
    new_import = "const { getAllInstances, getRemotes, runInstanceAction, listInstanceSnapshots, createInstanceSnapshot } = require('../incus');"

    if old_import in text:
        text = text.replace(old_import, new_import, 1)
    elif "listInstanceSnapshots" not in text:
        raise SystemExit("Could not patch routes/mobile.js incus import")

    if "router.get('/instances/:id/snapshots'" not in text:
        marker = "router.post('/instances/:id/actions/:action'"
        idx = text.find(marker)
        if idx == -1:
            raise SystemExit("Could not find action route insertion point in routes/mobile.js")

        text = text[:idx].rstrip() + "\n" + MOBILE_ROUTES + "\n\n" + text[idx:]

    MOBILE.write_text(text)
    print("Patched routes/mobile.js")

def main():
    if ROOT.name != "scottibyte-incus-mobile-server":
        print(f"WARNING: current directory is {ROOT}")

    backup()
    patch_incus()
    patch_mobile()

    print()
    print("Next commands:")
    print("  node --check incus.js")
    print("  node --check routes/mobile.js")
    print("  git diff -- incus.js routes/mobile.js")
    print()
    print("Backup:")
    print(f"  {BACKUP_DIR}")

if __name__ == "__main__":
    main()
