import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system';
import JSZip from 'jszip';

export type LoadedFile = {
  filename: string;
  title: string; // suggested title from filename
  body: string;
};

const HTML_ENTITIES: Record<string, string> = {
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&apos;': "'",
  '&#39;': "'",
  '&nbsp;': ' ',
  '&mdash;': '—',
  '&ndash;': '–',
  '&hellip;': '…',
  '&laquo;': '«',
  '&raquo;': '»',
  '&ldquo;': '"',
  '&rdquo;': '"',
  '&lsquo;': "'",
  '&rsquo;': "'",
};

function decodeEntities(s: string): string {
  return s
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(parseInt(n, 10)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCharCode(parseInt(n, 16)))
    .replace(/&[a-z][a-z0-9]+;/gi, (m) => HTML_ENTITIES[m.toLowerCase()] ?? m);
}

// Strip HTML tags, preserve block-level breaks as newlines.
export function htmlToText(html: string): string {
  return decodeEntities(
    html
      // Drop script/style blocks entirely (with content)
      .replace(/<(script|style)[^>]*>[\s\S]*?<\/\1>/gi, '')
      // Block-level tags become paragraph breaks
      .replace(/<\/(p|div|h[1-6]|li|blockquote|tr|article|section)>/gi, '\n\n')
      .replace(/<br\s*\/?>/gi, '\n')
      // Strip all remaining tags
      .replace(/<[^>]+>/g, '')
  )
    .replace(/[ \t]+/g, ' ')
    .replace(/\n[ \t]+/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

// Minimal markdown stripping. Mirrors the HTML version's cleaner.
export function stripMarkdown(text: string): string {
  return text
    // Code fences
    .replace(/```[\s\S]*?```/g, '')
    .replace(/`([^`]+)`/g, '$1')
    // Images
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    // Links [text](url) → text
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    // Headings
    .replace(/^#{1,6}\s+/gm, '')
    // Bold/italic markers
    .replace(/(\*\*|__)(.+?)\1/g, '$2')
    .replace(/(\*|_)(.+?)\1/g, '$2')
    // Strikethrough
    .replace(/~~(.+?)~~/g, '$1')
    // HRs
    .replace(/^[-*_]{3,}$/gm, '')
    // Blockquotes
    .replace(/^>\s?/gm, '')
    // List markers
    .replace(/^[\s]*[-*+]\s+/gm, '')
    .replace(/^[\s]*\d+\.\s+/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

// Pull the spine (chapter ordering) out of an OPF file.
function parseSpineOrder(opfXml: string): string[] {
  // Build idref → href map from <manifest><item id="..." href="..." />
  const items: Record<string, string> = {};
  const itemRegex = /<item\b([^>]+?)\/?>/g;
  let m: RegExpExecArray | null;
  while ((m = itemRegex.exec(opfXml)) !== null) {
    const attrs = m[1];
    const id = /\sid=["']([^"']+)["']/.exec(attrs)?.[1];
    const href = /\shref=["']([^"']+)["']/.exec(attrs)?.[1];
    if (id && href) items[id] = href;
  }
  // Walk <spine><itemref idref="..." />
  const order: string[] = [];
  const refRegex = /<itemref\b[^>]*?\sidref=["']([^"']+)["']/g;
  while ((m = refRegex.exec(opfXml)) !== null) {
    const href = items[m[1]];
    if (href) order.push(href);
  }
  return order;
}

function dirname(path: string): string {
  const i = path.lastIndexOf('/');
  return i < 0 ? '' : path.slice(0, i);
}

function joinPath(base: string, rel: string): string {
  if (!base) return rel;
  // Handle ../ in rel
  const parts = (base + '/' + rel).split('/');
  const out: string[] = [];
  for (const p of parts) {
    if (p === '..') out.pop();
    else if (p && p !== '.') out.push(p);
  }
  return out.join('/');
}

async function readEpubText(uri: string): Promise<string> {
  const base64 = await FileSystem.readAsStringAsync(uri, {
    encoding: FileSystem.EncodingType.Base64,
  });
  const zip = await JSZip.loadAsync(base64, { base64: true });

  // 1. Find the OPF via META-INF/container.xml
  const containerFile = zip.file('META-INF/container.xml');
  if (!containerFile) throw new Error('Not a valid EPUB: missing container.xml');
  const containerXml = await containerFile.async('string');
  const opfPath = /full-path=["']([^"']+)["']/.exec(containerXml)?.[1];
  if (!opfPath) throw new Error('Not a valid EPUB: cannot locate OPF');

  const opfFile = zip.file(opfPath);
  if (!opfFile) throw new Error(`OPF file not found: ${opfPath}`);
  const opfXml = await opfFile.async('string');

  const opfDir = dirname(opfPath);
  const order = parseSpineOrder(opfXml);
  if (order.length === 0) throw new Error('EPUB spine is empty');

  // 2. Concatenate all chapter texts in spine order
  const chapters: string[] = [];
  for (const href of order) {
    const full = joinPath(opfDir, href.split('#')[0]);
    const entry = zip.file(full);
    if (!entry) continue;
    const html = await entry.async('string');
    const text = htmlToText(html);
    if (text.trim()) chapters.push(text);
  }
  return chapters.join('\n\n');
}

export async function pickAndLoadFile(): Promise<LoadedFile | null> {
  const result = await DocumentPicker.getDocumentAsync({
    type: [
      'text/plain',
      'text/markdown',
      'application/epub+zip',
      'application/octet-stream',
      '*/*',
    ],
    copyToCacheDirectory: true,
    multiple: false,
  });
  if (result.canceled || !result.assets?.length) return null;
  const asset = result.assets[0];
  const filename = asset.name || 'Untitled';
  const lower = filename.toLowerCase();

  let body: string;
  if (lower.endsWith('.epub')) {
    body = await readEpubText(asset.uri);
  } else {
    const raw = await FileSystem.readAsStringAsync(asset.uri, {
      encoding: FileSystem.EncodingType.UTF8,
    });
    body = lower.endsWith('.md') || lower.endsWith('.markdown')
      ? stripMarkdown(raw)
      : raw;
  }

  const title = filename.replace(/\.(epub|txt|md|markdown)$/i, '');
  return { filename, title, body: body.trim() };
}
