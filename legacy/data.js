// Shared text storage + tokenization + sampling.
// Texts are kept in sessionStorage (per-tab memory) so newly-added entries
// survive page navigation but vanish when the tab closes.

// Sample texts ship with the app. The bodies live in samples.js (generated
// from peri-beni.txt + hobbit.txt) so this file stays readable. samples.js
// must be loaded before data.js — see <script> order in every HTML page.
const SAMPLE_TEXTS = [
  {
    id: 'peri-beni',
    title: "Peri Beni Nerelere Götürüyo'",
    body: typeof PERI_BENI_TEXT === 'string' ? PERI_BENI_TEXT : '',
  },
  {
    id: 'hobbit',
    title: 'Hobbit (Resimli) — J. R. R. Tolkien',
    body: typeof HOBBIT_TEXT === 'string' ? HOBBIT_TEXT : '',
  },
];

const STORE_KEY = 'memo.texts.v1';

function loadTexts() {
  try {
    const raw = sessionStorage.getItem(STORE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length) return parsed;
    }
  } catch (e) {}
  return SAMPLE_TEXTS.map(t => ({ ...t }));
}

function persist() {
  try { sessionStorage.setItem(STORE_KEY, JSON.stringify(TEXTS)); } catch (e) {}
}

let TEXTS = loadTexts();

function slugify(s) {
  return (s || '').toLowerCase()
    .replace(/['"`]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 40) || 'text';
}

function getText(id) {
  return TEXTS.find(t => t.id === id) || TEXTS[0];
}

function getAllTexts() { return TEXTS; }

function addText(title, body) {
  const base = slugify(title);
  let id = base, n = 1;
  while (TEXTS.find(t => t.id === id)) id = base + '-' + (++n);
  const text = { id, title: title.trim() || 'Untitled', body };
  TEXTS.push(text);
  persist();
  return text;
}

function removeText(id) {
  TEXTS = TEXTS.filter(t => t.id !== id);
  persist();
}

// Tokenize text into a flat token array + a per-line index map.
const WORD_REGEX = /\p{L}/u;
function tokenize(text) {
  const tokens = [];
  const lineMap = [];
  const lines = text.split('\n');
  lines.forEach((line, i) => {
    if (line.trim() === '') {
      tokens.push({ type: 'break' });
    } else {
      const lineTokenIdxs = [];
      const parts = line.split(/(\s+)/);
      parts.forEach(p => {
        if (p === '') return;
        const idx = tokens.length;
        if (/^\s+$/.test(p)) {
          tokens.push({ type: 'space', text: p });
        } else {
          tokens.push({
            type: 'token',
            text: p,
            isWord: WORD_REGEX.test(p),
            blanked: false,
            revealed: false
          });
        }
        lineTokenIdxs.push(idx);
      });
      lineMap.push(lineTokenIdxs);
      if (i < lines.length - 1) tokens.push({ type: 'newline' });
    }
  });
  return { tokens, lineMap };
}

// Inhibition sampling: picks repel each other via Gaussian falloff (blue-noise).
function inhibitionSample(n, target, sigma, strength = 0.9) {
  if (target >= n) return Array.from({ length: n }, (_, i) => i);
  if (target <= 0) return [];

  const weights = new Array(n).fill(1.0);
  const picked = [];

  for (let k = 0; k < target; k++) {
    let total = 0;
    for (let i = 0; i < n; i++) total += weights[i];
    if (total <= 1e-9) {
      for (let i = 0; i < n; i++) {
        if (!picked.includes(i)) { picked.push(i); break; }
      }
      continue;
    }
    let r = Math.random() * total;
    let idx = n - 1;
    for (let i = 0; i < n; i++) {
      r -= weights[i];
      if (r <= 0) { idx = i; break; }
    }
    picked.push(idx);
    const twoSigSq = 2 * sigma * sigma;
    for (let j = 0; j < n; j++) {
      const d = j - idx;
      const falloff = strength * Math.exp(-(d * d) / twoSigSq);
      weights[j] *= (1 - falloff);
    }
    weights[idx] = 0;
  }
  return picked;
}

function pickBlanksPerLine(tokens, lineMap, pct) {
  const blankSet = new Set();
  lineMap.forEach(lineIdxs => {
    const wordIdxs = lineIdxs.filter(i => tokens[i].type === 'token' && tokens[i].isWord);
    const n = wordIdxs.length;
    if (n === 0) return;
    const target = Math.round(n * pct / 100);
    if (target === 0) return;
    const sigma = Math.max(0.8, n / (2 * target));
    const picks = inhibitionSample(n, target, sigma);
    picks.forEach(p => blankSet.add(wordIdxs[p]));
  });
  return blankSet;
}

// Strip common Markdown syntax — keeps the text content, drops the noise.
function stripMarkdown(text) {
  return text
    .replace(/^---+\s*$/gm, '')             // hr
    .replace(/^\s{0,3}#{1,6}\s*/gm, '')     // headings (incl. empty "# ")
    .replace(/^\s*>\s?/gm, '')              // blockquote markers
    .replace(/^\s*[-*+]\s+/gm, '')          // unordered list
    .replace(/^\s*\d+\.\s+/gm, '')          // ordered list
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1') // image → alt
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1') // link → text
    .replace(/\*\*([^*]+)\*\*/g, '$1')      // **bold**
    .replace(/__([^_]+)__/g, '$1')          // __bold__
    .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1$2') // *italic*
    .replace(/(^|[^_])_([^_\n]+)_/g, '$1$2')   // _italic_
    .replace(/~~([^~]+)~~/g, '$1')          // ~~strike~~
    .replace(/`([^`]+)`/g, '$1')            // `code`
    .replace(/\\([\\`*_{}\[\]()#+\-.!|])/g, '$1'); // escaped chars
}

// Strip HTML tags + decode the common named entities. Block-level tags
// become a line break so paragraphs survive readable.
function stripHtml(text) {
  return text
    .replace(/<\s*(br|p|div|section|article|li|tr|h[1-6])\b[^>]*>/gi, '\n')
    .replace(/<\s*\/\s*(p|div|section|article|li|tr|h[1-6])\s*>/gi, '\n')
    .replace(/<!--[\s\S]*?-->/g, '')        // HTML comments
    .replace(/<\s*style\b[\s\S]*?<\s*\/\s*style\s*>/gi, '')
    .replace(/<\s*script\b[\s\S]*?<\s*\/\s*script\s*>/gi, '')
    .replace(/<[^>]+>/g, '')                // remaining tags
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/&apos;/gi, "'")
    .replace(/&hellip;/gi, '…')
    .replace(/&mdash;/gi, '—')
    .replace(/&ndash;/gi, '–')
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(+n))
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)));
}

// Normalize whitespace and collapse runs of blank lines.
function tidyText(text) {
  return text
    .replace(/\r\n?/g, '\n')
    .replace(/ /g, ' ')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n[ \t]+/g, '\n')
    .replace(/[ \t]{2,}/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .replace(/^\s+|\s+$/g, '');
}

// One-shot cleaner used by the "Add text" form. Order matters: HTML first
// (it may contain markdown-like chars), then markdown, then tidy.
function cleanText(text) {
  return tidyText(stripMarkdown(stripHtml(text)));
}

const PREF_KEY = 'memo.prefs.v1';
function getPrefs() {
  try {
    const raw = sessionStorage.getItem(PREF_KEY);
    if (raw) return JSON.parse(raw);
  } catch (e) {}
  return {};
}
function setPref(key, val) {
  const p = getPrefs();
  p[key] = val;
  try { sessionStorage.setItem(PREF_KEY, JSON.stringify(p)); } catch (e) {}
}
function getPref(key, fallback) {
  const p = getPrefs();
  return key in p ? p[key] : fallback;
}

// Resolve the active text for a mode page from the URL ?id= param.
// Applies the global "strip markdown" preference if enabled.
function getActiveText() {
  const id = new URLSearchParams(location.search).get('id');
  const t = getText(id);
  if (getPref('stripMd', false)) {
    return { ...t, body: stripMarkdown(t.body) };
  }
  return t;
}

function countWords(body) {
  const matches = body.match(/\p{L}+/gu);
  return matches ? matches.length : 0;
}

function countLines(body) {
  return body.split('\n').filter(l => l.trim() !== '').length;
}

// Paginate raw body lines into pages of ~targetWords each. Prefers breaking
// on blank-line paragraph boundaries. Returns [{ start, end }] inclusive
// line indices into the bodyLines array.
function paginate(bodyLines, targetWords = 800) {
  const pages = [];
  let start = 0;
  let words = 0;
  for (let i = 0; i < bodyLines.length; i++) {
    words += (bodyLines[i].match(/\p{L}+/gu) || []).length;
    const isBreak = bodyLines[i].trim() === '';
    if (words >= targetWords && (isBreak || i === bodyLines.length - 1)) {
      pages.push({ start, end: i });
      start = i + 1;
      words = 0;
    }
  }
  if (start < bodyLines.length) {
    if (pages.length) {
      const tail = bodyLines.slice(start).join(' ').match(/\p{L}+/gu);
      const tailWords = tail ? tail.length : 0;
      if (tailWords < targetWords / 4) {
        pages[pages.length - 1].end = bodyLines.length - 1;
      } else {
        pages.push({ start, end: bodyLines.length - 1 });
      }
    } else {
      pages.push({ start, end: bodyLines.length - 1 });
    }
  }
  return pages.length ? pages : [{ start: 0, end: bodyLines.length - 1 }];
}
