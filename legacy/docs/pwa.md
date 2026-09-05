# PWA Implementation Plan — memo-poc

Shippable plan to turn `memo-poc` (vanilla HTML/CSS/JS, no build, GitHub Pages at `/memo-poc/`) into an installable, offline-capable PWA. Verified May 2026 — some 2023 patterns (e.g. `purpose: "any maskable"`) are now discouraged; corrected below.

---

## 1. What we're making installable

Goal: open the Pages URL once online, "Add to Home Screen", from then on the app launches standalone (no browser chrome) and works in airplane mode.

**Must work offline:** `index.html`, all 6 mode pages (`spread.html`, `focus.html`, `first-letter.html`, `typing.html`, `rsvp.html`, `bionic.html`), `style.css`, `data.js`, `reader.js`, and `samples.js` (582 KB — both sample bodies are baked in).

**`hobbit.txt` (570 KB):** the file itself isn't loaded at runtime — its body is already inside `samples.js` as `HOBBIT_TEXT`. Do NOT precache `hobbit.txt` or `peri-beni.txt`. Do precache `samples.js` eagerly (compresses to ~250 KB over the wire); offline samples are the whole point.

**Install UX:**
- **Android / desktop Chrome:** `beforeinstallprompt` fires → suppress default infobar → show our own "Install" button in the home header.
- **iOS Safari:** no install event. User taps Share → "Add to Home Screen". We show a one-time dismissible tooltip explaining this.

---

## 2. Files to add

### 2.1 `manifest.json` (repo root)

Relative paths so the same file works at `localhost:8000/` and `erkantaylan.github.io/memo-poc/`.

```json
{
  "name": "Memorize",
  "short_name": "Memorize",
  "description": "Memorization and fast-reading practice for any text.",
  "start_url": "./",
  "scope": "./",
  "display": "standalone",
  "orientation": "any",
  "theme_color": "#1c1a16",
  "background_color": "#1c1a16",
  "lang": "tr",
  "dir": "ltr",
  "categories": ["education", "books", "productivity"],
  "icons": [
    {
      "src": "icons/icon-192.png",
      "sizes": "192x192",
      "type": "image/png",
      "purpose": "any"
    },
    {
      "src": "icons/icon-512.png",
      "sizes": "512x512",
      "type": "image/png",
      "purpose": "any"
    },
    {
      "src": "icons/icon-maskable-512.png",
      "sizes": "512x512",
      "type": "image/png",
      "purpose": "maskable"
    }
  ]
}
```

Use **separate** `any` and `maskable` entries — combined `"any maskable"` causes wrong padding on some platforms. Maskable icons need a 20% safe zone (logo in the central 80%).

### 2.2 Icon files

`icons/` at repo root:

| File | Size | Purpose |
|---|---|---|
| `icons/icon-192.png` | 192×192 | Chrome installability minimum |
| `icons/icon-512.png` | 512×512 | Large icon |
| `icons/icon-maskable-512.png` | 512×512 | Android adaptive (safe-zone centered) |
| `icons/apple-touch-icon.png` | 180×180 | iOS home screen |
| `icons/favicon.ico` | 32×32 | Browser tab |

**Generation:** make one 1024×1024 source PNG (`icons/source.png`, `#1c1a16` bg, logo in center 800×800), then either:
- `npx pwa-asset-generator icons/source.png ./icons --no-index --no-manifest --favicon` (recommended);
- upload to https://realfavicongenerator.net and drop the zip into `icons/`;
- or a sharp one-liner per size.

Only manual step. ~30 min, once.

### 2.3 `<head>` additions for every HTML page

All 7 pages need this snippet inside `<head>`, after the existing `<link rel="stylesheet">`:

```html
<link rel="manifest" href="manifest.json">
<meta name="theme-color" content="#1c1a16">
<link rel="icon" href="icons/favicon.ico" sizes="any">
<link rel="icon" type="image/png" sizes="192x192" href="icons/icon-192.png">

<!-- iOS-specific -->
<link rel="apple-touch-icon" href="icons/apple-touch-icon.png">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<meta name="apple-mobile-web-app-title" content="Memorize">

<!-- Viewport (add only if missing — required for mobile) -->
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">

<script src="pwa.js" defer></script>
```

### 2.4 `pwa.js` (repo root)

One bootstrap, included on every page. Handles SW registration, install button, iOS tooltip.

```javascript
// pwa.js — service worker registration + install UX
(function () {
  if (!('serviceWorker' in navigator)) return;

  // Register relative to current page so it works under /memo-poc/ on Pages
  // and under / on localhost. The browser resolves the URL against the page.
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('service-worker.js', { scope: './' })
      .then((reg) => {
        // Update flow: listen for a new worker waiting and notify the user.
        reg.addEventListener('updatefound', () => {
          const nw = reg.installing;
          if (!nw) return;
          nw.addEventListener('statechange', () => {
            if (nw.state === 'installed' && navigator.serviceWorker.controller) {
              showUpdateToast(nw);
            }
          });
        });
      })
      .catch((err) => console.warn('SW registration failed:', err));

    // Reload once when the new SW takes control.
    let refreshing = false;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (refreshing) return;
      refreshing = true;
      window.location.reload();
    });
  });

  function showUpdateToast(waitingWorker) {
    const el = document.createElement('div');
    el.setAttribute('role', 'status');
    el.style.cssText =
      'position:fixed;left:50%;bottom:1rem;transform:translateX(-50%);' +
      'background:#2a2620;color:#d8cfb8;border:1px solid #3a352b;' +
      'padding:.6rem .9rem;border-radius:.5rem;z-index:9999;' +
      'font:14px -apple-system,Segoe UI,Roboto,sans-serif;' +
      'box-shadow:0 4px 16px rgba(0,0,0,.4);display:flex;gap:.6rem;align-items:center';
    el.innerHTML =
      '<span>Updated — reload to apply.</span>' +
      '<button style="background:#d99e5a;color:#1c1a16;border:0;' +
      'padding:.3rem .6rem;border-radius:.3rem;font-weight:600;cursor:pointer">Reload</button>' +
      '<button aria-label="dismiss" style="background:transparent;color:#8c8473;' +
      'border:0;cursor:pointer;font-size:18px;line-height:1">×</button>';
    const [, reloadBtn, dismissBtn] = el.querySelectorAll('span, button, button');
    el.children[1].onclick = () => waitingWorker.postMessage({ type: 'SKIP_WAITING' });
    el.children[2].onclick = () => el.remove();
    document.body.appendChild(el);
  }

  // --- Install button (Android/desktop Chrome) ---
  let deferredPrompt = null;
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    const btn = document.getElementById('installBtn');
    if (btn) btn.hidden = false;
  });

  document.addEventListener('click', async (e) => {
    if (e.target && e.target.id === 'installBtn' && deferredPrompt) {
      const p = deferredPrompt;
      deferredPrompt = null;
      e.target.hidden = true;
      p.prompt();
      await p.userChoice; // {outcome: 'accepted'|'dismissed'}
    }
  });

  window.addEventListener('appinstalled', () => {
    const btn = document.getElementById('installBtn');
    if (btn) btn.hidden = true;
    deferredPrompt = null;
  });

  // --- iOS one-time tooltip ---
  const ua = navigator.userAgent;
  const isIOS = /iPad|iPhone|iPod/.test(ua) && !window.MSStream;
  const isStandalone =
    window.matchMedia('(display-mode: standalone)').matches ||
    window.navigator.standalone === true;
  const shown = localStorage.getItem('memo.iosA2HS.shown') === '1';
  const onHome = /(^|\/)index\.html?$/.test(location.pathname) || location.pathname.endsWith('/');
  if (isIOS && !isStandalone && !shown && onHome) {
    window.addEventListener('load', () => setTimeout(showIOSHint, 1200));
  }

  function showIOSHint() {
    const el = document.createElement('div');
    el.style.cssText =
      'position:fixed;left:1rem;right:1rem;bottom:1rem;background:#221f1a;' +
      'color:#d8cfb8;border:1px solid #3a352b;border-radius:.6rem;' +
      'padding:.8rem 1rem;z-index:9999;font:14px -apple-system,sans-serif;' +
      'box-shadow:0 4px 16px rgba(0,0,0,.4)';
    el.innerHTML =
      '<strong style="color:#d99e5a">Install Memorize</strong><br>' +
      'Tap <span aria-hidden="true">⬆︎</span> Share, then "Add to Home Screen".' +
      '<button style="float:right;background:transparent;color:#8c8473;' +
      'border:0;cursor:pointer;font-size:18px;margin-top:-2px">×</button>';
    el.querySelector('button').onclick = () => {
      localStorage.setItem('memo.iosA2HS.shown', '1');
      el.remove();
    };
    document.body.appendChild(el);
  }
})();
```

### 2.5 `service-worker.js` (repo root)

```javascript
// service-worker.js — memo-poc PWA cache
// Bump CACHE_VERSION on every deploy that ships new assets.
const CACHE_VERSION = 'v1-2026-05-11';
const CACHE_NAME = `memo-${CACHE_VERSION}`;

// App shell: precached at install time. Paths are relative to the SW location,
// which is the repo root (so works under /memo-poc/ on Pages and / on localhost).
const APP_SHELL = [
  './',
  './index.html',
  './spread.html',
  './focus.html',
  './first-letter.html',
  './typing.html',
  './rsvp.html',
  './bionic.html',
  './style.css',
  './data.js',
  './reader.js',
  './samples.js',
  './pwa.js',
  './manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/apple-touch-icon.png',
  './icons/favicon.ico',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) =>
      // Use {cache: 'reload'} so we bypass HTTP cache during precache.
      cache.addAll(APP_SHELL.map((u) => new Request(u, { cache: 'reload' })))
    )
  );
  // Note: we do NOT call self.skipWaiting() here. We wait for the user to
  // accept the toast, which posts SKIP_WAITING. This avoids breaking pages
  // mid-session by swapping the SW underneath them.
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const names = await caches.keys();
      await Promise.all(
        names.filter((n) => n.startsWith('memo-') && n !== CACHE_NAME).map((n) => caches.delete(n))
      );
      await self.clients.claim();
    })()
  );
});

self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SKIP_WAITING') self.skipWaiting();
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);

  // Same-origin only. Don't touch cross-origin (no third parties today, but
  // protects against future CDN-loaded fonts etc.).
  if (url.origin !== self.location.origin) return;

  // Navigation requests: network-first, fall back to cached index.html.
  // This makes the app feel snappy online and still work offline.
  if (req.mode === 'navigate') {
    event.respondWith(
      (async () => {
        try {
          const fresh = await fetch(req);
          const cache = await caches.open(CACHE_NAME);
          cache.put(req, fresh.clone());
          return fresh;
        } catch {
          const cached = await caches.match(req);
          return cached || caches.match('./index.html');
        }
      })()
    );
    return;
  }

  // Static assets: cache-first, fall through to network and cache the response.
  event.respondWith(
    (async () => {
      const cached = await caches.match(req);
      if (cached) return cached;
      try {
        const fresh = await fetch(req);
        if (fresh && fresh.status === 200 && fresh.type === 'basic') {
          const cache = await caches.open(CACHE_NAME);
          cache.put(req, fresh.clone());
        }
        return fresh;
      } catch (e) {
        // No network, no cache — let it fail.
        return Response.error();
      }
    })()
  );
});
```

---

## 3. Service worker strategy details

- **App shell:** precached at `install`. ~620 KB total — well under any quota.
- **Navigation requests:** network-first with cache fallback. Cached copy refreshed on every successful fetch, so deploys propagate even without a version bump.
- **Static assets (CSS/JS/icons):** cache-first. Already precached; new files cached on first hit.
- **`samples.js`:** precache eagerly — it IS the offline content.
- **Don't cache:** cross-origin, non-GET, future `/api/` paths (gate by `url.pathname`), the raw `.txt` files (unused at runtime).
- **Update flow:** new SW installs → `installed` (waiting) → `pwa.js` shows toast → user taps Reload → toast posts `SKIP_WAITING` → SW activates → `controllerchange` fires → page reloads once → old cache deleted in `activate`.
- **Cache invalidation:** bump `CACHE_VERSION`. Optional: inject commit SHA at deploy time (§8).

---

## 4. iOS-specific quirks

iOS Safari ignores `theme_color`, `background_color`, `display`, and most manifest fields. The meta tags in §2.3 are required:

- `apple-mobile-web-app-capable=yes` → standalone mode.
- `apple-mobile-web-app-status-bar-style=black-translucent` → content extends under status bar (pair with `viewport-fit=cover` + `env(safe-area-inset-top)` if you need padding).
- `apple-mobile-web-app-title` → home-screen label.
- `apple-touch-icon` 180×180 → home-screen icon (manifest icons ignored).

**Splash screens:** iOS flashes white on launch unless you add `apple-touch-startup-image` link tags — one per device resolution (10+ images). Skip for v1; trade-off is a ~500 ms flash on cold launch, unnoticeable thereafter. To add later: `npx pwa-asset-generator icons/source.png ./icons --splash-only --dark-mode` produces images + matching `<link>` tags to paste into each `<head>`.

**Input gotchas:**
- iOS ignores `autofocus` and programmatic `.focus()` unless inside a user gesture. Home page's `titleInput.focus()` fires in a click handler — fine, keep as is.
- We use `sessionStorage` (not IndexedDB) so pre-iOS-17 SW/IDB bugs don't apply.
- `sessionStorage` is per-tab in standalone mode too — texts added in the PWA don't appear in Safari. Existing behavior.

---

## 5. Add-to-home-screen prompt UX

Already wired in `pwa.js` (§2.4):

- **Android/desktop Chrome:** `beforeinstallprompt` handler stashes the event, reveals `#installBtn`. No auto-popup.
- **`index.html` header markup** (add when you wire the button):
  ```html
  <button id="installBtn" hidden style="margin-left:auto">Install app</button>
  ```
- **iOS Safari:** UA-detect + non-standalone + first-visit → bottom-sheet tooltip pointing at the Share button. One-time via `localStorage`.
- **Already installed:** suppressed via `display-mode: standalone` / `appinstalled` event / `iosA2HS.shown` flag.

---

## 6. Testing checklist

PWAs require HTTPS — `localhost` is exempt.

```powershell
# Pick one:
python -m http.server 8000
npx serve .
# LAN test from a phone needs real HTTPS:
mkcert -install; mkcert localhost
npx http-server -S -C localhost.pem -K localhost-key.pem -p 8443
```

- [ ] Chrome → DevTools → **Application**:
  - **Manifest** panel: icons resolve, "Installability" green.
  - **Service Workers**: `activated and running`. Toggle "Offline" → reload still works.
  - **Cache Storage**: `memo-v1-2026-05-11` has all 16 shell entries.
- [ ] **Lighthouse → PWA**: target ≥ 90. Expected loss: no splash screens.
- [ ] **Android:** install via our button, launch from launcher (no URL bar), airplane mode → all 6 modes load.
- [ ] **iOS:** see tooltip, Share → Add to Home Screen, launch standalone, airplane mode.
- [ ] **Update flow:** bump `CACHE_VERSION`, redeploy, refresh tab → toast → Reload → page reloads on new version.

---

## 7. GitHub Pages compatibility notes

- Pages serves HTTPS → SW works.
- Site is at `https://erkantaylan.github.io/memo-poc/` (subpath).
- **Critical:** all paths in `manifest.json`, `pwa.js`, and `service-worker.js` are relative (`./...`), so the same files work at `localhost:8000/` and on Pages.
- The SW at `…/memo-poc/service-worker.js` with `scope: './'` resolves to scope `/memo-poc/` — covers the app, doesn't bleed into sibling repos on the same `erkantaylan.github.io` origin.
- Renaming the repo doesn't require code changes; only previously-installed PWAs keep the old URL until reinstalled.

---

## 8. Deploy delta

- **`.gitignore`:** no new entries (`node_modules/` already covered).
- **`.github/workflows/deploy.yml`:** no changes — `upload-pages-artifact@v3` with `path: '.'` already ships the new files.
- **Cache-busting:** don't use `?v=hash` query strings — they balloon the SW cache. Rely on `CACHE_VERSION`. To auto-stamp it, add before `Upload artifact`:
  ```yaml
  - name: Stamp service worker
    run: |
      SHA=$(git rev-parse --short HEAD)
      sed -i "s/v1-2026-05-11/$SHA/" service-worker.js
  ```

---

## 9. Future PWA features (don't build now)

- **Push notifications:** Chrome/Edge always; iOS 16.4+ only when installed and EU-flaky. Needs VAPID + server. Defer to `mobile.md` if we add a backend.
- **Background Sync:** only useful once there's a backend.
- **File Handling API:** register as handler for `.txt`/`.md` — Chrome-only, just add `"file_handlers"` to manifest.
- **Web Share Target:** receive shared text via system share sheet. Pairs with existing "Add text" form.
- **Periodic Sync / Badging:** low value here.

---

## 10. Order of operations (shipping checklist)

Each step under 30 min.

1. **Create `manifest.json`** from §2.1. (5 min)
2. **Generate icons** — 1024×1024 source → `pwa-asset-generator` → `icons/` has the 5 files in §2.2. (30 min, one-time)
3. **Paste `<head>` snippet (§2.3) into all 7 HTML pages.** (15 min)
4. **Create `service-worker.js`** from §2.5. (5 min)
5. **Create `pwa.js`** from §2.4. (5 min)
6. **Local test:** `python -m http.server 8000` → DevTools → Application → manifest valid, SW active, 16 cache entries, offline works. Lighthouse PWA ≥ 90. (20 min)
7. **Add `#installBtn` to `index.html` header** (optional polish; without it, Android install still works via the URL-bar icon). (5 min)
8. **Deploy:** push, wait for Pages action, open on Android phone, install, airplane-mode-test 6 modes. (20 min)
9. **iOS test:** open in Safari, tooltip → Add to Home Screen, launch standalone, airplane-mode-test. (15 min)
10. **Optional: wire `CACHE_VERSION` to commit SHA** via §8 workflow step. (10 min)

Total: ~2–2.5 hours, mostly the one-time icon step.

---

## Sources

- Web app manifest current spec — https://web.dev/learn/pwa/web-app-manifest
- Maskable icons / `purpose` field guidance — https://dev.to/progressier/why-a-pwa-app-icon-shouldnt-have-a-purpose-set-to-any-maskable-4c78
- iOS PWA limitations 2026 — https://www.magicbell.com/blog/pwa-ios-limitations-safari-support-complete-guide
- `beforeinstallprompt` patterns — https://web.dev/learn/pwa/installation-prompt
- SW update flow — https://developer.chrome.com/docs/workbox/handling-service-worker-updates
- `skipWaiting` reference — https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/skipWaiting
- GitHub Pages PWA scope notes — https://christianheilmann.com/2022/01/13/turning-a-github-page-into-a-progressive-web-app/
- pwa-asset-generator — https://github.com/elegantapp/pwa-asset-generator
