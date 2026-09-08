# yammer bridge (mobile)

Android app that connects an on-prem **DATECS DP-25MX cash register** (over USB-OTG
serial) to the yammer backend, so waiter CASH / CARD / ONLINE payments print fiscal
receipts. Ported from the old yammer project's `bridge-mobile`.

## What it does

- Keeps an outbound WebSocket to the backend (`wss://…/ws/bridge`), authenticated with
  the `X-Bridge-Key` header (= the backend's `BRIDGE_API_KEY` secret). Auto-reconnects
  every 10 s; OkHttp ping/pong is the heartbeat. Announces itself with a `HELLO` frame
  carrying a stable per-install **device id** — paste it into Backoffice → Peripherals
  on the cash register row (connection USB) so receipts route to this phone.
- Frames: in `RECEIPT` (fiscal → DATECS over USB; `fiscal=false` → ESC/POS printer over
  TCP :9100) and `INFO_RECEIPT` (non-fiscal proforma); out `RECEIPT_RESULT`.
- **Exactly-once**: a write-ahead print INTENT + processed `requestId` journal
  (`processed-receipts.jsonl`, fsync, 7-day TTL). A re-sent id answers from the cache;
  an orphan intent (app died mid-print) answers `UNKNOWN` and never re-prints until an
  operator resolves it from the Payments report ("Not printed" → the next RECEIPT carries
  `clearIntent`). Every inbound frame is answered (IN_FLIGHT / BRIDGE_INTERNAL) and a job
  is capped at 90 s — strictly below the backend's 180 s result deadline.
- Runs as a foreground service (survives backgrounding; restarts on boot). "Comenzi"
  lists failed receipts with a local Retry.

## Setup on the phone

1. Install the APK, open it, set a **device name** (nickname shown in the backoffice
   device picker), **Server URL** (default = the api's run.app URL), **API key**, baud
   (115200) and till number; tap *Salveaza si (re)porneste* — the name is announced in the
   next HELLO.
2. Plug the register in with an OTG cable, tap *Conecteaza USB*, accept the permission,
   then *Test casa* (sends a harmless cancel — prints nothing).
3. Copy the Device ID (tap it) into Backoffice → Peripherals → the register's USB row.

## Build

```bash
# needs the Android SDK (local.properties: sdk.dir=…) and JDK 17+
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
