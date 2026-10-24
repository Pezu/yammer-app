# ADR-0002 — Single-use ticket for browser WebSocket authentication

Status: accepted · Epic: #1 · Date: 2026-10-24

## Context

Browsers cannot set an `Authorization` header on a WebSocket, so the service board connects to
`/ws/orders?token=<jwt>` and `WsAuthHandshakeInterceptor` authenticates from the query string.
The JWT lives 24 h and the client reconnects every 3 s while disconnected — valid staff session
tokens are therefore continuously written into nginx and Cloud Run request logs, readable by
anyone with log-viewer access (finding W2/A13, major).

Alternatives considered:

1. **`Sec-WebSocket-Protocol` smuggling** — pass the JWT as a subprotocol value. No new
   endpoint, but nonstandard, requires the server to echo a subprotocol back, and some stacks
   log the header anyway.
2. **Shorten the JWT lifetime** — shrinks the exposure window but punishes every surface with
   re-logins to fix one transport, and the token still lands in logs.
3. **Single-use short-lived ticket** — an authenticated `POST /ws/ticket` mints an opaque
   random value; the handshake consumes it.

## Decision

The ticket. `POST /ws/ticket` (authenticated) returns a 128-bit random value stored in an
in-memory map with the username and a 30 s expiry; `WsAuthHandshakeInterceptor` resolves and
consumes it from `?ticket=`. The client requests a fresh ticket before every (re)connect.
`?token=` stays accepted for one release for rollback safety, then is removed.

## Consequences

- What appears in request logs is a value that is already used (single-use) or expires within
  30 s — worthless to an attacker with log access.
- The ticket store is in-memory, which is only correct because the api is pinned to ONE Cloud
  Run instance — the same constraint the WS session registries already impose (CLAUDE.md §5
  "single instance"). Anything that moves the api to multiple instances must move this store
  (and the registries) first; that is already an ADR-level change by the §5 gate.
- One extra round-trip before each (re)connect; negligible against the 3 s reconnect cadence.
- The bridge handshake is unaffected (machine client, header auth); its redundant `?key=`
  query fallback is dropped separately (finding A13-key).
