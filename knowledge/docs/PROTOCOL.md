# Local API and device protocol

The local companion API is described by its generated OpenAPI document at `/openapi.json`.
The app uses the same origin for all API requests and rejects redirects.

| Endpoint | Purpose |
| --- | --- |
| `GET /health` | Process readiness and whether owner provisioning is required |
| `POST /auth/login`, `/auth/logout` | Owner session creation/revocation |
| `GET /state` | Saved desired settings, library, rules, capabilities and live connection metadata |
| `POST /actions` | Explicit lighting, microphone, calibration or IR request |
| `GET /jobs/{id}` | Final/uncertain command outcome |
| `/appliances`, `/commands`, `/presets`, `/rules` | Validated collection management |
| `/learning`, `/learning/{id}`, `/learning/{id}/save` | Guided capture session lifecycle |
| `PUT /settings` | Time zone, OLED timeout, volume preference and global automation switch |
| `POST /backup`, `/restore` | Encrypted archive export and transactional import |
| `GET /diagnostics` | Bounded command and event histories |
| `/spotify/connect`, `/spotify/callback` | Browser PKCE authorization with expiring state |
| `/spotify/{player,devices,playlists,recent,search}`, `/spotify/playback` | Authenticated Spotify operations |
| `POST /ota` | Confirmed signed manifest, rejected unless the device advertises OTA |

Owner requests use `Authorization: Bearer <token>`. Tokens are hashed in SQLite and
expire after twelve hours. The device uses a separate `X-Device-Key`; owner tokens
cannot authorize device routes and device keys cannot authorize owner routes.

Device endpoints: heartbeat, config snapshot, job fetch, job ack, learning callback,
and authenticated IR event. `GET /device/jobs` atomically changes pending jobs to
delivered. It never delivers a job twice. Jobs expire after 15 seconds (30 for learning,
60 for OTA). Restarted outstanding jobs become uncertain. Nothing is automatically
replayed. Reusing an `Idempotency-Key` with the same action returns the original job;
using it for a different action returns 409.

Outcome states include `pending`, `delivered`, `applied`, `transmitted`, `unsupported`,
`failed`, `uncertain`, `expired`, `cancelled`, and `simulated`. Only the first two
are waiting states. `transmitted` is not confirmed appliance state. `simulated`
never means physical output occurred. `/state` lighting values are saved requested
settings, not a measured appliance state.

Device-owned scheduling is reported by `automation_owner: device` in heartbeat.
The server then does not run a duplicate copy of schedules/events. The snapshot
contains UTC-minute occurrences calculated using explicit IANA zones over nine
local dates. The portable firmware skips its first valid minute, backward clocks,
and gaps. It starts disarmed after reboot and requires a fresh authenticated sync.

The Python process must have one worker. Multiple workers would create competing
in-memory device leases and OAuth state stores; do not add `--workers` or deploy
behind a load balancer without implementing shared ownership first.
