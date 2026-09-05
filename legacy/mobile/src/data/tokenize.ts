import { LETTER_REGEX } from './canonicalize';

export type Token =
  | { type: 'token'; text: string; isWord: boolean; idx: number }
  | { type: 'space'; text: string; idx: number };

export type Line = {
  raw: string;
  tokens: Token[]; // tokens belonging to this line (no breaks)
  isBlank: boolean;
};

export type Tokenized = {
  lines: Line[];
};

export function tokenize(text: string): Tokenized {
  const lines: Line[] = [];
  const rawLines = text.split('\n');
  let runningIdx = 0;
  rawLines.forEach((raw) => {
    if (raw.trim() === '') {
      lines.push({ raw: '', tokens: [], isBlank: true });
      return;
    }
    const parts = raw.split(/(\s+)/);
    const lineTokens: Token[] = [];
    parts.forEach((p) => {
      if (p === '') return;
      if (/^\s+$/.test(p)) {
        lineTokens.push({ type: 'space', text: p, idx: runningIdx++ });
      } else {
        lineTokens.push({
          type: 'token',
          text: p,
          isWord: LETTER_REGEX.test(p),
          idx: runningIdx++,
        });
      }
    });
    lines.push({ raw, tokens: lineTokens, isBlank: false });
  });
  return { lines };
}

export function countWords(body: string): number {
  return (body.match(/\p{L}+/gu) || []).length;
}

export function countLines(body: string): number {
  return body.split('\n').filter((l) => l.trim() !== '').length;
}
