Server `v1.6.0` and Android client `v0.7.0` add instance IP-address reporting and detailed nested Docker visibility.

## Server v1.6.0

### Added

- Added primary IPv4 reporting for running Incus instances.
- Containers report only the global IPv4 address assigned to `eth0`.
- Virtual machines use the first suitable guest-agent-reported non-loopback and non-Docker interface.
- Added a direct single-instance detail lookup to avoid rescanning every Incus server when opening an instance.
- Added authoritative nested Docker detection for running containers.
- Nested Docker details distinguish:
  - Docker not installed
  - Docker installed with the daemon stopped
  - Docker running with no configured containers
  - Docker running with active and total configured container counts
  - Unknown when the detail probe genuinely fails
- Added a short nested Docker detail cache.

### Improved

- Nested Docker detection runs only for the selected instance rather than during full inventory refresh.
- Removed unreliable Docker detection based only on bridge-interface names.
- Preserved compact multi-server inventory refresh behavior.

## Android Client v0.7.0

### Added

- Added IPv4 addresses to running instance list cards without increasing card height.
- Added IP Address to the instance detail card.
- Added detailed nested Docker status to running container detail cards.
- Added active and configured nested Docker container counts.
- Added a visible Checking state while Docker details load.

### Fixed

- Fixed virtual machines displaying a literal `null` address.
- Fixed virtual machines not using guest-agent-reported interfaces such as `enp5s0`.
- Fixed nested Docker detail requests being sent without the mobile bearer token.
- Fixed nested Docker details disappearing after refreshing an open instance.
- Fixed stale inventory data replacing authoritative Docker detail results.

## Android APK

`ScottiBYTE-Incus-Mobile-Android-v0.7.0-debug.apk`
