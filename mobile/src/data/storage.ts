import AsyncStorage from '@react-native-async-storage/async-storage';
import { SAMPLE_TEXTS, Sample } from './samples';

const KEY_CUSTOM = 'memo.customTexts.v1';
const KEY_PREFS = 'memo.prefs.v1';

export type Text = Sample;

// Sample texts always available; user-added texts persist across launches.
export async function loadAllTexts(): Promise<Text[]> {
  try {
    const raw = await AsyncStorage.getItem(KEY_CUSTOM);
    const custom: Text[] = raw ? JSON.parse(raw) : [];
    return [...SAMPLE_TEXTS, ...custom];
  } catch {
    return [...SAMPLE_TEXTS];
  }
}

export async function saveCustomTexts(custom: Text[]): Promise<void> {
  try {
    await AsyncStorage.setItem(KEY_CUSTOM, JSON.stringify(custom));
  } catch {}
}

export async function addCustomText(title: string, body: string): Promise<Text> {
  const id = slugify(title) + '-' + Date.now().toString(36);
  const t: Text = { id, title: title.trim() || 'Untitled', body };
  const raw = await AsyncStorage.getItem(KEY_CUSTOM);
  const custom: Text[] = raw ? JSON.parse(raw) : [];
  custom.push(t);
  await saveCustomTexts(custom);
  return t;
}

export async function removeCustomText(id: string): Promise<void> {
  const raw = await AsyncStorage.getItem(KEY_CUSTOM);
  const custom: Text[] = raw ? JSON.parse(raw) : [];
  await saveCustomTexts(custom.filter((t) => t.id !== id));
}

function slugify(s: string): string {
  return (s || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 40) || 'text';
}

export async function getTextById(id: string): Promise<Text | null> {
  const all = await loadAllTexts();
  return all.find((t) => t.id === id) || null;
}

// --- Preferences (theme, etc.) ---
export type Prefs = {
  themeName?: 'sepia' | 'dark' | 'light';
  fontScale?: number; // 0.8 .. 1.6
};

export async function loadPrefs(): Promise<Prefs> {
  try {
    const raw = await AsyncStorage.getItem(KEY_PREFS);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

export async function savePrefs(p: Prefs): Promise<void> {
  try {
    await AsyncStorage.setItem(KEY_PREFS, JSON.stringify(p));
  } catch {}
}
