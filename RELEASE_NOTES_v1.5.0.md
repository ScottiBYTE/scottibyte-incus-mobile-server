Server `v1.5.0` and Android client `v0.6.0` add resilient multi-server inventory and complete mobile snapshot management.

## Server v1.5.0

### Added

- Added concurrent inventory scanning across configured Incus remotes.
- Added partial inventory results when one or more remotes are unavailable.
- Added TCP reachability checks for failed remote inventory requests.
- Added distinct Online, Offline, No Quorum, and Inventory Error states.
- Added admin-only snapshot APIs for taking, listing, restoring, renaming, and deleting snapshots.
- Added protected-instance enforcement for snapshot operations.
- Added global mobile-actions enforcement for snapshot operations.

### Improved

- Added hard timeouts and process termination for stalled Incus commands.
- Improved remote failure messages while retaining detailed diagnostics.
- Reduced duplicate inventory scans in the web admin dashboard.
- Improved behavior when an Incus cluster has lost quorum.
- Prevented one failed remote from blocking inventory from healthy remotes.

## Android Client v0.6.0

### Added

- Added **Take Snapshot** and **Manage Snapshots** actions for Admin clients.
- Added snapshot Restore, Rename, and Delete controls.
- Added newest-first snapshot ordering.
- Added Restore only for the newest snapshot.
- Added an explanation of the newest-snapshot restore restriction.
- Added dark-themed snapshot management, confirmation, and rename dialogs.
- Added a visible snapshot-list scroll indicator.
- Added immediate progress and success feedback for snapshot operations.

### Improved

- Improved presentation of Offline, No Quorum, and Inventory Error states.
- Improved partial-inventory behavior when servers are unavailable.
- Improved remote failure messages shown to users.
- Fixed an Android UI-thread error during client identity updates.
- Increased the Android inventory request timeout for slower multi-server scans.
- Updated the Android version balloon and update target for v0.6.0.

## Android APK

`ScottiBYTE-Incus-Mobile-Android-v0.6.0-debug.apk`
