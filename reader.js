// Shared reader-options panel + style application.
// Used by every mode page. Prefs live in sessionStorage via getPref/setPref
// from data.js, so changes persist across page navigation within the tab.

const READER_FONTS = {
  serif: "'Iowan Old Style', Charter, 'Source Serif Pro', Cambria, Georgia, serif",
  sans:  "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
  mono:  "'Cascadia Code', Consolas, 'Courier New', monospace",
};

const READER_DEFS = {
  font:    { type: 'select', label: 'Font',
             options: [['serif','Serif'],['sans','Sans-serif'],['mono','Monospace']],
             def: 'serif' },
  theme:   { type: 'select', label: 'Theme',
             options: [['sepia','Sepia (default)'],['dark','Neutral dark'],['light','Light (paper)']],
             def: 'sepia' },
  size:    { type: 'range', label: 'Font size',     min:14,  max:36,  step:1,  def:20,  fmt: v => v + 'px' },
  boldPct: { type: 'range', label: 'Bold %',        min:20,  max:80,  step:5,  def:50,  fmt: v => v + '%' },
  lh:      { type: 'range', label: 'Line height',   min:120, max:260, step:5,  def:195, fmt: v => (v/100).toFixed(2) },
  ls:      { type: 'range', label: 'Letter spacing',min:0,   max:20,  step:1,  def:0,   fmt: v => (v/100).toFixed(2) + 'em' },
  ws:      { type: 'range', label: 'Word spacing',  min:0,   max:50,  step:2,  def:0,   fmt: v => (v/100).toFixed(2) + 'em' },
  width:   { type: 'range', label: 'Width',         min:400, max:1200,step:20, def:720, fmt: v => v + 'px' },
  stripMd: { type: 'check', label: 'Strip Markdown formatting (reloads page; applies to all modes)', def: false },
};

function applyReaderTheme() {
  if (!document.body) {
    document.addEventListener('DOMContentLoaded', applyReaderTheme, { once: true });
    return;
  }
  document.body.classList.remove('theme-sepia', 'theme-dark', 'theme-light');
  const t = getPref('theme', 'sepia');
  if (t !== 'sepia') document.body.classList.add('theme-' + t);
}

function applyReaderStyles(textEl, include) {
  if (!textEl) return;
  const has = k => include.includes(k);
  if (has('font'))  textEl.style.fontFamily = READER_FONTS[getPref('font', 'serif')];
  if (has('size'))  textEl.style.fontSize = getPref('size', 20) + 'px';
  if (has('lh'))    textEl.style.lineHeight = getPref('lh', 195) / 100;
  if (has('ls'))    textEl.style.letterSpacing = (getPref('ls', 0) / 100) + 'em';
  if (has('ws'))    textEl.style.wordSpacing = (getPref('ws', 0) / 100) + 'em';
  if (has('width')) {
    textEl.style.maxWidth = getPref('width', 720) + 'px';
    textEl.style.marginLeft = 'auto';
    textEl.style.marginRight = 'auto';
  }
}

function installReaderOptions({ textEl = null, include = [], onChange = () => {}, openByDefault = false } = {}) {
  applyReaderTheme();
  if (!include.length) return;

  const panel = document.createElement('details');
  panel.className = 'opts';
  if (openByDefault) panel.open = true;

  let html = '<summary>Reader options</summary><div class="opt-grid">';
  include.forEach(key => {
    const d = READER_DEFS[key];
    if (!d) return;
    if (d.type === 'select') {
      html += `<label>${d.label}<select data-ro="${key}">`;
      d.options.forEach(([v, l]) => html += `<option value="${v}">${l}</option>`);
      html += `</select><span class="val"></span></label>`;
    } else if (d.type === 'range') {
      html += `<label>${d.label}<input type="range" data-ro="${key}" min="${d.min}" max="${d.max}" step="${d.step}"><span class="val"></span></label>`;
    } else if (d.type === 'check') {
      html += `<label class="full checkbox"><input type="checkbox" data-ro="${key}"><span>${d.label}</span></label>`;
    }
  });
  html += '</div>';
  panel.innerHTML = html;

  const header = document.querySelector('header');
  header.parentNode.insertBefore(panel, header.nextSibling);

  panel.querySelectorAll('[data-ro]').forEach(el => {
    const key = el.dataset.ro;
    const d = READER_DEFS[key];
    const cur = getPref(key, d.def);
    if (el.type === 'checkbox') el.checked = !!cur;
    else el.value = cur;
    const valEl = el.parentElement.querySelector('.val');
    if (valEl && d.fmt) valEl.textContent = d.fmt(cur);

    const evt = (el.tagName === 'SELECT' || el.type === 'checkbox') ? 'change' : 'input';
    el.addEventListener(evt, () => {
      const v = el.type === 'checkbox' ? el.checked : (el.type === 'range' ? +el.value : el.value);
      setPref(key, v);
      if (valEl && d.fmt) valEl.textContent = d.fmt(v);
      if (key === 'theme') applyReaderTheme();
      if (key === 'stripMd') { location.reload(); return; }
      applyReaderStyles(textEl, include);
      onChange(key, v);
    });
  });

  applyReaderStyles(textEl, include);
  return panel;
}

// Apply theme immediately so every page picks up the user's choice
// the moment data.js + reader.js have loaded.
applyReaderTheme();
