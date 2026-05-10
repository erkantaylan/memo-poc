// Turkish-aware character canonicalization. All chars in the same group are
// treated as equivalent for matching purposes (used by typing + speak modes).
const TR_GROUPS = [
  'aAâÂ',
  'cCçÇ',
  'eE',
  'gGğĞ',
  'iIıİîÎ',
  'oOöÖ',
  'sSşŞ',
  'uUüÜûÛ',
] as const;

const CANON_MAP: Record<string, string> = {};
for (const g of TR_GROUPS) {
  const key = g[0].toLowerCase();
  for (const ch of g) CANON_MAP[ch] = key;
}

export function canon(ch: string): string {
  if (ch in CANON_MAP) return CANON_MAP[ch];
  return ch.toLowerCase();
}

export function canonWord(word: string): string {
  return [...word].map(canon).join('');
}

export const LETTER_REGEX = /\p{L}/u;
export const isLetter = (ch: string): boolean => LETTER_REGEX.test(ch);
