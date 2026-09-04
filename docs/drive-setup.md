# Google Drive setup

One-time Cloud Console work, then the library syncs with one command. Only you
can do part 1 — it is browser clicks against your own Google account.

## Why these particular choices

| Choice | Reason |
|---|---|
| **`drive.file` scope** | Non-sensitive. No app verification, no CASA security assessment. `drive.readonly` is a *restricted* scope and would require an annual paid audit. |
| **Publish to "In production"** | An app left in **Testing** issues refresh tokens that **expire after 7 days**, for every scope except basic identity. Publishing removes that. With only non-sensitive scopes, publishing needs no verification. |
| **Desktop OAuth client** | Installed-app clients reliably receive refresh tokens. |
| **Flat folder on Drive** | Files are stored as `<item-id>.<ext>` in one folder. Structure lives in `catalog.json`, so Drive never has to mirror the tree. |

`drive.file` only ever grants access to files **this app created**. A leaked
token reaches the library folder and nothing else in your Drive.

## Part 1 — Cloud Console (you)

1. **Create a project** at <https://console.cloud.google.com/projectcreate>.
   Name it `kitaplik`. Note the **project number** — `drive.file` grants are
   tied to the project, not to an individual client.

2. **Enable the Drive API**: APIs & Services → Library → search "Google Drive
   API" → **Enable**.

3. **Branding** (Google Auth Platform → Branding): app name `Kitaplık`, your
   email as support and developer contact. No logo — uploading one triggers
   brand verification you do not need.

4. **Audience** → User type **External** → then **PUBLISH APP** so the status
   reads **In production**, not *Testing*. This is the step that stops the
   7-day token expiry. There is no verification prompt for non-sensitive
   scopes.

5. **Data Access** → Add or remove scopes → filter for `drive.file` → tick
   `.../auth/drive.file`. **Do not** add `drive` or `drive.readonly`.

6. **Clients** → Create client → type **Desktop app** → name `gog` → Create →
   **Download JSON**.

## Part 2 — authorize gog (either of us)

```bash
gog auth credentials set ~/Downloads/client_secret_*.json
gog auth add you@gmail.com --services drive --drive-scope file
gog auth doctor --check
```

`auth add` opens a browser for consent. Because the app is published but
unverified, you may see an "unverified app" notice — with non-sensitive scopes
only, continue past it; the 100-user cap and danger screen apply to sensitive
and restricted scopes.

Confirm the scope that was actually granted:

```bash
gog auth list --json
```

It must show `drive.file` and nothing broader.

## Part 3 — push the library

```bash
node ../books/scripts/build-catalog.mjs --root ../library --out ../library/catalog.json
python3 tools/drive_sync.py --library ../library --dry-run   # check first
python3 tools/drive_sync.py --library ../library
```

The sync creates the `kitaplik-library` folder, uploads anything missing,
writes each `drive_id` back into `catalog.json`, and uploads the catalog last
so it never describes files that are not yet there. Re-running only uploads
what changed.

## Still to settle

Whether a `drive.file` grant is shared across OAuth clients **within one Cloud
project** is not documented by Google. Strong evidence says yes — the Picker
requires an `appId` that is literally the *project number*, and Google states
the project "must contain both the client ID and the app ID as it's used to
authorize access to a user's files" — but it is not stated outright, and
community reports disagree (from tests that changed project and client
together, proving only cross-*project* isolation).

It matters because `gog` uploads with the Desktop client while the Android app
would authenticate with its own client. Two ways forward:

1. **Test it.** Upload with gog, then list from an Android-client token. Settles
   it in about twenty minutes.
2. **Sidestep it.** Give the app the same Desktop client's refresh token, so
   both sides are literally the same OAuth client and the question cannot
   arise.

Decide before building `DriveCatalogSource`.
