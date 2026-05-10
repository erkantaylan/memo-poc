# memo-poc Backend Design

Solo-dev plan for adding a backend to the currently 100% client-side
memorization app, using the Cloudflare domain the user already owns.

---

## 1. What requires a backend (and what doesn't)

The app works today with zero backend. Keep it that way until a feature
genuinely needs server state.

| Feature | Verdict | Why |
|---|---|---|
| Text library (current `sessionStorage`) | client-only | Move to `localStorage` first — survives reloads, zero infra |
| The 6 memorization modes (spread, first-letter, focus, typing, RSVP, bionic) | client-only | Pure rendering / DOM logic |
| Reader prefs (font size, line height) | client-only | `localStorage` |
| File upload of `.md` / `.txt` | client-only | Parsed in the browser today, leave it there |
| Pre-loaded Turkish samples | client-only | Ship as static JS like today |
| **Cross-device sync** | backend-required | Need a server of record |
| **Account / login** | backend-required | Identity needs a trusted third party |
| **Spaced-repetition state shared across devices** | either | Single-device → `localStorage`. Multi-device → backend |
| **Public share links** (`/share/:token`) | backend-required | Anyone with the link must fetch it |
| Speech-mode memorization scoring | client-only | Web Speech API does STT in the browser; no server cost, no privacy headache |
| Premium / Stripe | backend-required | Stripe webhooks need a public HTTPS endpoint and a DB |
| Analytics | third-party | Plausible (€9/mo cheapest paid), PostHog free tier (1M events/mo) — don't roll your own |
| Backups | backend-required | Only meaningful if data exists on a server |

**Rule: don't stand up a backend until users ask for sync OR you want to
sell something.** `localStorage` + Export/Import JSON carries the product far.

---

## 2. Recommended platform

The user owns a Cloudflare-registered domain. That tilts the answer, but here
is the honest comparison.

| Stack | Free tier (2026) | Latency | Complexity | Lock-in |
|---|---|---|---|---|
| **Cloudflare Workers + D1 + KV + R2** | 100k Worker req/day, 10ms CPU; D1: 5GB / 5M reads / 100k writes daily; KV 1GB / 100k reads daily; R2 10GB, no egress | Edge, <50ms globally | Low once you accept Workers' runtime quirks | Medium — D1 is SQLite, schema portable; Workers code is not |
| **Supabase** | 500MB Postgres, 50k MAU auth, 1GB file storage; **auto-pauses after 7 days idle** | Single region | Lowest — auth + Postgres + storage + RLS out of the box | High — RLS/Auth coupling |
| **Firebase** | Spark plan; 1GB Firestore, 10GB transfer/mo | Multi-region | Low | Highest — document model is non-portable |
| **Vercel + Neon Postgres** | Vercel hobby 100GB bandwidth; Neon free 0.5GB | US/EU regions | Medium | Medium |
| **Railway / Render / Fly** | Trial credits, no real free tier any more | Single region | Low (it's just Docker) | Low — plain Postgres+Node |
| **Hetzner VPS (CX22)** | None — €3.79/mo, 2 vCPU / 4GB / 40GB | Single DC | Highest — you own the box | Zero |

**Pick: Cloudflare Workers + D1 + R2.**

1. The domain is already on Cloudflare → DNS, TLS, and `api.<domain>` route
   are trivial.
2. Free tier covers 1k DAU at $0/month (see §6).
3. No idle-pause penalty (Supabase's biggest gotcha for a side project).
4. D1 is SQLite — schema is portable to a Hetzner box later. The Worker
   runtime is the only real lock-in.
5. Static HTML/JS keeps living on Cloudflare Pages or GitHub Pages for free.

Fallback if you hate JS on the server: Hetzner CX22 + Node + Litestream-backed
SQLite. Same data model, ~€4/mo.

---

## 3. Data model

D1 is SQLite. Schema below; everything is ASCII, indexed where the API hits it.

```sql
-- Users. anon_token lets a fresh visitor own data before signing up.
CREATE TABLE users (
  id            TEXT PRIMARY KEY,         -- UUIDv7
  email         TEXT UNIQUE,              -- NULL for anonymous accounts
  display_name  TEXT,
  anon_token    TEXT UNIQUE,              -- opaque; rotated on upgrade
  created_at    INTEGER NOT NULL,         -- epoch ms
  upgraded_at   INTEGER                   -- when anon -> email
);
CREATE INDEX idx_users_email ON users(email);

-- Texts. body inline for now; offload to R2 if > 256 KB (see notes).
CREATE TABLE texts (
  id          TEXT PRIMARY KEY,           -- UUIDv7
  owner_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title       TEXT NOT NULL,
  body        TEXT,                       -- NULL when offloaded to R2
  body_r2_key TEXT,                       -- e.g. "texts/<owner>/<id>.txt"
  byte_len    INTEGER NOT NULL,
  is_public   INTEGER NOT NULL DEFAULT 0, -- 0/1
  share_token TEXT UNIQUE,                -- NULL unless is_public
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL
);
CREATE INDEX idx_texts_owner ON texts(owner_id, updated_at DESC);
CREATE INDEX idx_texts_share ON texts(share_token) WHERE share_token IS NOT NULL;

-- Per-(user, text, mode) progress. SRS state is opaque JSON.
CREATE TABLE progress (
  user_id            TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text_id            TEXT NOT NULL REFERENCES texts(id) ON DELETE CASCADE,
  mode               TEXT NOT NULL,       -- 'spread'|'first-letter'|'focus'|'typing'|'rsvp'|'bionic'
  score              REAL NOT NULL DEFAULT 0,
  last_practiced_at  INTEGER,
  srs_state          TEXT,                -- JSON (FSRS / SM-2 state blob)
  PRIMARY KEY (user_id, text_id, mode)
);

-- Practice sessions. Append-only event log; cheap to roll up later.
CREATE TABLE sessions (
  id            TEXT PRIMARY KEY,
  user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text_id       TEXT NOT NULL REFERENCES texts(id) ON DELETE CASCADE,
  mode          TEXT NOT NULL,
  started_at    INTEGER NOT NULL,
  finished_at   INTEGER,
  words_total   INTEGER,
  words_correct INTEGER,
  mistakes      TEXT                       -- JSON array of indices
);
CREATE INDEX idx_sessions_user_time ON sessions(user_id, started_at DESC);
```

### Notes on storing `body` in D1

D1 enforces a **2 MB max row size**. The Hobbit sample is ~570 KB — fits as
`TEXT`. Strategy:

- `byte_len < 256 KB` → inline in `body`. Single round trip.
- `byte_len ≥ 256 KB` → write to R2 as `texts/<owner>/<id>.txt`, set
  `body_r2_key`, leave `body NULL`. Worker streams on `GET /texts/:id`.
- Skip gzip until storage actually bites.

---

## 4. API surface

REST, JSON, JWT bearer auth. Hosted at `api.<your-domain>` via a Worker route.

```
POST   /auth/anon              {} -> { token, user_id }
POST   /auth/signup            { email } -> { ok: true }            # sends magic link
POST   /auth/login             { email } -> { ok: true }            # sends magic link
GET    /auth/callback?t=<otp>  -> 302 to app with session cookie + JWT
POST   /auth/upgrade           Authorization: Bearer <anon_jwt>
                               { email } -> { ok: true }            # links email to anon user_id
POST   /auth/logout            -> { ok: true }

GET    /texts                  -> [{ id, title, byte_len, is_public, updated_at }, ...]
POST   /texts                  { title, body } -> { id, ... }
GET    /texts/:id              -> { id, title, body, is_public, share_token?, ... }
PATCH  /texts/:id              { title?, body?, is_public? } -> { id, ... }
DELETE /texts/:id              -> 204

GET    /progress                            -> [{ text_id, mode, score, srs_state, last_practiced_at }, ...]
POST   /progress/event         { text_id, mode, delta_score, srs_state, finished_at } -> { ok: true }

GET    /sessions?text_id&limit -> [{ id, mode, started_at, words_total, words_correct }, ...]
POST   /sessions               { text_id, mode, started_at, finished_at, words_total, words_correct, mistakes } -> { id }

GET    /share/:token           -> { title, body }                    # public, no auth
```

Conventions:
- All responses are JSON; errors `{ error: { code, message } }`.
- 401 if JWT missing/expired. Client refreshes by hitting `/auth/anon` (anon
  users) or re-magic-linking.
- `If-Modified-Since` / `ETag` on `GET /texts` for cheap polling sync.

---

## 5. Auth

**Anonymous-first, email-magic-link to upgrade.**

- **Anonymous-first**: friction kills adoption. First load → client silently
  `POST /auth/anon`, stores JWT in `localStorage`, data is persisted. No login
  screen ever appears unless the user wants sync.
- **Magic links over passwords**: no hashing, no resets, no "did you mean
  .con", no breach liability. Only downside is email deliverability.
- **No social OAuth at v1.** Three vendor relationships for marginal benefit.
- **JWT, not server sessions.** Workers are stateless; a signed JWT in
  `Authorization: Bearer ...` avoids a DB lookup per request. HS256 + Worker
  secret binding; 30-day expiry; `kid` for rotation.

Library on Workers: **`better-auth`** — edge-native, designed for Workers +
D1, magic-link plugin built in. Lucia v3 works too but its maintainer points
new builds at better-auth. Clerk's free tier (50k MRUs) is generous but
outsources account-delete and data-export to a third party — skip until you
don't want to own auth. Cloudflare Access is for SSO, wrong tool here.

**Upgrade flow** (`POST /auth/upgrade`): client sends anon JWT + email.
Worker sets `users.email`, `users.upgraded_at` on the existing row and sends
a magic link. `user_id` is stable so all FKs follow — no data migration. This
is the single most important detail for friction-free UX.

---

## 6. Cost projection

**Assumptions: 1000 DAU, 10 texts each, 50 progress events/day.**

| Daily load | Volume | CF free limit | Headroom |
|---|---|---|---|
| Worker requests | ~70k (5 reads + 50 events + a few writes per DAU) | 100k/day | ~30% buffer |
| Worker CPU | Each request <5ms (D1 query + JSON) | 10ms/req | Easy fit |
| D1 row reads | ~700k (10 texts × 70k req conservative) | 5M/day | 7x headroom |
| D1 row writes | ~55k (events + sessions) | 100k/day | ~45% buffer |
| D1 storage | 1000 users × 10 texts × ~50 KB avg = ~500 MB | 5 GB | 10x headroom |
| R2 storage | Only for >256 KB texts; few hundred MB | 10 GB | Fine |
| KV | Use only for rate limits / share tokens | 1 GB / 100k reads/day | Trivial |

**Cost at 1000 DAU: $0/month.**

**Email (magic links)** is the first thing to outgrow free tier:
- Resend free: 3,000/mo, **100/day cap**. At 1000 DAU with a 5% weekly login
  rate, you send ~50/day — fits.
- Outgrow it: Resend Pro $20/mo for 50k/mo, or AWS SES ~$0.10/1000.

**When you cross to paid**: D1 daily writes is the likely first ceiling. The
Workers Paid plan is $5/mo and gives 10M req/mo, 50ms CPU/req, plus 50M D1
writes/month. At ~10k DAU you'll be paying $10–15/mo all-in.

**Analytics**: PostHog free (1M events/mo) or Plausible (€9/mo). Don't build
your own.

---

## 7. Migration / sync from client-only state

Existing users have data in `sessionStorage` — which dies on tab close, so
it's mostly already lost. Going forward:

1. **Phase 0** (do regardless of backend): switch the client from
   `sessionStorage` to `localStorage`. One-line change, survives reloads.
2. **First backend release**: on load, if `localStorage.memo_texts` exists
   and no JWT yet → silently `POST /auth/anon`, bulk-upload via `POST /texts`,
   set a `migrated_at` flag so re-runs are no-ops.
3. **Sign-in conflict**: compare incoming texts vs server texts by
   `(title, byte_len)` hash. No collision → import. Collision → prompt
   "Keep both / Replace / Skip"; default to keep both with `(2)` appended.
4. **Export / Import JSON**:
   ```
   GET  /export -> { schema_version, texts, progress, sessions }
   POST /import { ...same shape... } -> { imported, skipped }
   ```
   Doubles as the GDPR data-export endpoint.
5. App must **keep working without a backend forever**. Wrap every API call
   in `try { ... } catch { useLocalOnly() }`.

---

## 8. Operational concerns

- **Backups**: D1 Time Travel gives point-in-time restore. Belt-and-braces:
  nightly Worker cron dumps every table to R2 under `backups/YYYY-MM-DD.json`.
  R2 is effectively free under 10 GB.
- **Monitoring**: built-in Workers analytics (requests, errors, CPU) is free.
  Sentry free tier (5k errors/mo) for both client JS and Worker errors.
- **Rate limiting**: KV counter keyed by `user_id`; 60 req/min anon, 600/min
  upgraded. Stops abuse without a WAF.
- **Migrations**: `migrations/NNN_description.sql` + `wrangler d1 migrations
  apply`. Solo-dev sufficient.
- **GDPR**:
  - `DELETE /auth/account` cascades via `ON DELETE CASCADE` and deletes the
    R2 objects under `texts/<user>/*`. Returns 204.
  - `GET /export` (see §7) dumps everything for the authenticated user.
- **Secrets**: `wrangler secret put`, never in code.

---

## 9. Future-but-cheap extensions

Flagged, not built:

- **Realtime collaborative editing** of shared texts — Durable Objects + Y.js,
  one DO per text. Pay-as-you-go, cheap.
- **Public-link sharing** — already in the schema (`share_token`,
  `GET /share/:token`), only needs a UI toggle.
- **Class / group features** — `groups` table and `group_members(user_id,
  group_id, role)`; texts get optional `group_id`. Don't build until a
  teacher asks.
- **Server-side SRS scheduling** — promote `next_due_at` from inside
  `srs_state` to a real indexed column when you want cards-due-today queries.
- **Mobile apps** (iOS/Android roadmap) — the JSON API is the contract; a
  React Native / Expo client reuses it with zero backend changes.

---

## Sources

- [Cloudflare Workers limits](https://developers.cloudflare.com/workers/platform/limits/)
- [Cloudflare D1 limits](https://developers.cloudflare.com/d1/platform/limits/)
- [Cloudflare Workers KV limits](https://developers.cloudflare.com/kv/platform/limits/)
- [Cloudflare R2 pricing](https://developers.cloudflare.com/r2/pricing/)
- [Supabase pricing](https://supabase.com/pricing)
- [Resend pricing](https://resend.com/pricing)
- [Clerk pricing](https://clerk.com/pricing)
- [Hetzner Cloud pricing](https://www.hetzner.com/cloud)
- [better-auth + Cloudflare Workers](https://github.com/zpg6/better-auth-cloudflare)
- [D1 2 MB row limit discussion](https://dev.to/morphinewan/when-cloudflare-d1s-2mb-limit-taught-me-a-hard-lesson-about-database-design-3edb)
