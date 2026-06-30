# ScottiBYTE Incus Mobile Android Client

**ScottiBYTE Incus Mobile** is the Android companion app for ScottiBYTE Incus Mobile Server. It provides mobile visibility and controlled administration for Incus servers and instances.

The Android client connects to the ScottiBYTE Incus Mobile Server API. It does not connect directly to Incus servers and does not require Incus credentials on the phone.

## Screenshots

### Server List

![ScottiBYTE Incus Mobile Android Server List](../screenshots/scottibyte-incus-mobile-client-server-list.png)

### Instance List

![ScottiBYTE Incus Mobile Android Instance List](../screenshots/scottibyte-incus-mobile-client-instance-list.png)

### Instance Details

![ScottiBYTE Incus Mobile Android Instance Details](../screenshots/scottibyte-incus-mobile-client-instance-details.png)

### Mobile Shell

![ScottiBYTE Incus Mobile Android Shell](../screenshots/scottibyte-incus-mobile-client-shell.png)

## Features

- Pair with ScottiBYTE Incus Mobile Server
- View configured Incus servers
- View server reachability
- Browse instances by server
- Search instance inventory
- View instance details
- Role-aware mobile actions
- Start, stop, and restart instances
- Admin-only shell access
- Global action hiding when mobile actions are disabled on the server
- Read-only mode for Viewer role
- Operator mode for power control
- Admin mode for shell access

## How It Works

The Android app talks only to the ScottiBYTE Incus Mobile Server.

```text
Android Client
    |
    | HTTPS / mobile API token
    v
ScottiBYTE Incus Mobile Server
    |
    | Incus CLI / trusted Incus remotes
    v
Incus Servers
```

This keeps Incus trust, SSH setup, and operational policy centralized on the server.

## Roles

The actions visible in the Android app depend on the role assigned to the mobile client in the server admin dashboard.

| Role | Android Access |
|---|---|
| Viewer | View servers, instances, status, and inventory |
| Operator | Viewer access plus start, stop, and restart |
| Admin | Operator access plus shell access |

If the global mobile action switch is disabled on the server, all action buttons are hidden and the app remains read-only.

## Pairing Flow

1. Install the Android client.
2. Enter the ScottiBYTE Incus Mobile Server URL.
3. The device registers with the server.
4. The admin authorizes the device in the web dashboard.
5. The admin assigns the device a role.
6. The Android app refreshes and shows the role-appropriate features.

## Server URL

Use the URL for your ScottiBYTE Incus Mobile Server deployment.

Examples:

```text
https://incusmobile.example.com
```

or

```text
http://172.16.2.233:3088
```

HTTPS is recommended for production or remote access.

## Server List View

The server list summarizes configured Incus servers and their instance counts.

The app shows:

- Total servers
- Reachable server count
- Running instance count
- Stopped instance count
- Inventory issues

Selecting a server opens the instance list for that server.

## Instance List View

The instance list shows instances on the selected server.

The app shows:

- Instance name
- Status
- Type
- Project

The search box filters visible instances.

## Instance Details View

The instance details page shows:

- Instance name
- Server
- Project
- Type
- Status
- Architecture
- Created time
- Available actions

For authorized roles, action buttons may include:

- Stop
- Start
- Restart
- Shell

## Mobile Shell

Admin clients can open a shell session into eligible running containers.

Shell access is controlled by the server and is only available when:

- The mobile client role is Admin
- The global mobile action switch is enabled
- Mobile terminal support is enabled on the server
- The target instance is allowed
- The target is a running container

Shell sessions are logged in the server audit history.

## Safety Model

The Android app is intentionally not a full Incus administration replacement. Its purpose is safe mobile access for common operational needs.

The app depends on server-side policy for:

- Client authorization
- Role assignment
- Action availability
- Protected instances
- Audit logging
- Global action enable/disable

## Recommended Use

Use the Android client for:

- Checking server health
- Checking instance status
- Quickly starting, stopping, or restarting an instance
- Opening an emergency admin shell
- Verifying mobile operational state

Use the full Incus CLI or administrative workstation for:

- Initial Incus setup
- Storage configuration
- Network configuration
- Image management
- Complex migrations
- Snapshot restore workflows

## Requirements

- Android device
- Network access to ScottiBYTE Incus Mobile Server
- A paired and authorized mobile client record
- Server v1.0.0 or compatible
- Mobile API access enabled on the server

## Versioning

The Android client and server are versioned independently after the first public release.

Recommended public launch versions:

```text
Server: v1.0.0
Android Client: v1.0.0
API Compatibility: v1
```

Future Android releases may work with older server releases when the API remains compatible.

## Security Notes

- Do not pair unknown devices.
- Revoke lost or replaced phones immediately.
- Use Viewer role when mobile action access is not required.
- Use Operator role only for users who should perform power actions.
- Use Admin role only for trusted users who need shell access.
- Keep HTTPS enabled when using the app outside a trusted LAN.
