# ScottiBYTE Incus Mobile Server

**ScottiBYTE Incus Mobile Server** is a self-hosted mobile administration gateway for Incus. It provides a secure web-based admin console and an Android client for viewing Incus servers, browsing instances, performing controlled instance operations, and opening an admin-only mobile shell.

The project is designed for homelab and small infrastructure environments where you want safe mobile access to Incus without exposing direct Incus credentials to every device.

## Screenshot

![ScottiBYTE Incus Mobile Server Admin Dashboard](screenshots/scottibyte-incus-mobile-server-dashboard.png)

## Features

- Web-based admin dashboard
- First-run admin setup with mandatory 2FA
- Mobile client authorization and role assignment
- Viewer, Operator, and Admin role policy
- Global mobile actions safety switch
- Incus server management from the web UI
- Server online/offline visibility
- Instance inventory across configured Incus servers
- Start, stop, and restart controls for authorized mobile clients
- Admin-only mobile shell access
- Protected instance support
- Recent Activity audit log
- Adjustable audit display length
- CSV audit export
- Local admin credential reset command

## Architecture

ScottiBYTE Incus Mobile Server acts as a controller between Android clients and one or more Incus servers.

```text
Android Client
    |
    | HTTPS / token-authenticated mobile API
    v
ScottiBYTE Incus Mobile Server
    |
    | Incus CLI / Incus remote trust
    v
Incus Servers
```

The server maintains its own mobile client authorization database and Incus client configuration. Android clients never need direct Incus credentials.

## Role Model

| Role | Access |
|---|---|
| Viewer | Read-only inventory access |
| Operator | Start, stop, and restart instances |
| Admin | Start, stop, restart, and shell access |

The server also includes a global mobile action switch. When disabled, mobile clients remain read-only and the server rejects mobile action requests.

## Docker Deployment

The recommended deployment model is Docker Compose.

```yaml
services:
  incus-mobile-server:
    image: scottibyte/incus-mobile-server:1.0.0
    container_name: scottibyte-incus-mobile-server
    restart: unless-stopped

    ports:
      - "3088:3088"

    environment:
      APP_NAME: "ScottiBYTE Incus Mobile Server"
      PORT: "3088"
      DATA_DIR: "/app/data"
      APP_TIME_ZONE: "America/Chicago"

      TRUST_PROXY: "true"
      ADMIN_ALLOWED_CIDRS: "127.0.0.1/32,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16"

      MOBILE_ACTIONS_ENABLED: "true"
      MOBILE_TERMINAL_ENABLED: "true"
      MOBILE_TERMINAL_IDLE_TIMEOUT_MS: "900000"
      MOBILE_PROTECTED_INSTANCES: "IncusMobileServer"

    volumes:
      - ./data:/app/data

    # Option A: use your LAN DNS servers.
    # dns:
    #   - 172.16.1.10
    #   - 172.16.1.11

    # Option B: define static host mappings for Incus servers.
    # extra_hosts:
    #   - "mondo:172.16.1.225"
    #   - "vmsmist:172.16.1.50"
    #   - "vmsrain:172.16.1.51"
```

Start the server:

```bash
docker compose up -d
```

Then open:

```text
http://<docker-host>:3088/admin
```

## Incus Server Name Resolution

The web UI can add Incus servers by hostname or IP address. Since the server runs inside a container, the container must be able to resolve and reach the name you enter.

There are three supported approaches.

### Option 1: Use IP Addresses

This is the simplest and most reliable option.

```text
Incus Host: 172.16.2.14
```

No local DNS is required.

### Option 2: Use LAN DNS

If your LAN already has local DNS through Pi-hole, Unbound, Active Directory, or another resolver, configure the container to use those DNS servers.

```yaml
dns:
  - 172.16.1.10
  - 172.16.1.11
```

Then the Add Incus Server form can use names like:

```text
vmsmist
vmsrain
mondo
```

### Option 3: Use Docker Compose `extra_hosts`

This is the Docker-friendly equivalent of adding entries to `/etc/hosts`.

```yaml
extra_hosts:
  - "mondo:172.16.1.225"
  - "vmsmist:172.16.1.50"
  - "vmsrain:172.16.1.51"
```

This is useful when you do not want to depend on DNS or when the Docker host cannot resolve the same names as the rest of the LAN.

## Incus Server SSH Requirements

The Add Incus Server workflow uses SSH to help establish Incus trust and configure the remote.

Each Incus server you add must have:

- SSH reachable from the container
- An SSH user and password accepted by the target server
- Incus installed and initialized
- Incus API reachable on the configured port, usually `8443`
- Firewall rules allowing the container to reach SSH and the Incus API

The server image should include the client-side tools needed to perform this workflow, such as the Incus CLI, OpenSSH client, and SSH password support.

## First-Run Setup

On first access to `/admin`, create the web admin account and enroll 2FA.

The admin console controls:

- Incus server authorization
- Mobile client authorization
- Role assignment
- Mobile operation policy
- Audit visibility and export

## Adding an Incus Server

From the web dashboard, enter:

- Name
- Incus host or IP address
- Incus API port
- SSH user
- SSH port
- SSH password
- Trust name

The server will attempt to establish Incus trust, add the Incus server locally, and verify connectivity.

## Pairing Android Clients

After the server is configured, install the Android client and point it at the server URL.

Mobile clients appear in the admin dashboard as pending clients until authorized. Once authorized, each client can be assigned one of the available roles:

- Viewer
- Operator
- Admin

## Audit Log

The Recent Activity section records administrative and operational activity, including:

- Mobile operation requests
- Operation success or failure
- Client authorization changes
- Role changes
- Shell sessions
- Admin credential reset events
- Failed server tests

Successful automatic server health checks are not logged to avoid flooding the audit history.

The dashboard includes selectable display sizes:

```text
25 / 50 / 100 / 250 / 500
```

CSV export is available for deeper audit review.

## Local Admin Credential Reset

If you lose access to the web admin account or 2FA device, use the local reset command on the server:

```bash
scottibyte-incus-mobile-reset-admin
```

This resets only the web admin account and clears admin sessions.

It does **not** remove:

- Mobile clients
- Incus servers
- App settings
- Operation policy
- Audit history

After running the reset, visit `/admin` to complete first-run admin setup again.

## Persistent Data

The server stores persistent state in the configured data directory.

For Docker:

```text
/app/data
```

This should be mounted as a volume:

```yaml
volumes:
  - ./data:/app/data
```

Persistent data includes:

- SQLite database
- Admin settings
- Mobile client authorizations
- Operation policy
- Audit history
- Incus client configuration

## Security Notes

- Use HTTPS when exposing the admin console beyond a trusted LAN.
- Keep admin access limited to trusted networks.
- Use strong admin credentials and 2FA.
- Assign mobile clients the least privilege role required.
- Keep the global mobile action switch disabled when operational changes are not needed.
- Use protected instances for infrastructure containers that should not be controlled from mobile.

## Suggested Reverse Proxy

The server can sit behind a reverse proxy such as Nginx Proxy Manager, Caddy, Traefik, or Nginx.

When using a reverse proxy, set:

```yaml
TRUST_PROXY: "true"
```

## Project Status

ScottiBYTE Incus Mobile Server is intended as a safe mobile companion for Incus administration. It is not intended to replace the Incus CLI or full administrative workflows, but it provides fast mobile visibility and controlled operational access.
