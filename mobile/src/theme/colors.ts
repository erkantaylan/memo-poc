// Mirrors style.css theme tokens.
export const sepia = {
  bg:           '#1c1a16',
  fg:           '#d8cfb8',
  muted:        '#8c8473',
  accent:       '#d99e5a',
  blankBg:      '#28251e',
  blankBorder:  '#4a4538',
  revealed:     '#a3c982',
  hint:         '#e3c889',
  border:       '#3a352b',
  secondaryBg:  '#2e2b24',
  cardBg:       '#221f1a',
  cardInnerBg:  '#2a2620',
  inputBg:      '#16140f',
  badFlash:     '#b03a3a',
} as const;

export type Theme = typeof sepia;
export const colors: Theme = sepia;
