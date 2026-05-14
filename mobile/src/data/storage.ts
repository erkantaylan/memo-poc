import AsyncStorage from '@react-native-async-storage/async-storage';
import { SAMPLE_TEXTS, Sample } from './samples';

const KEY_CUSTOM = 'memo.customTexts.v1';
const KEY_PREFS = 'memo.prefs.v1';
const KEY_PROGRESS = 'memo.progress.v1';
const KEY_BOOKMARKS = 'memo.bookmarks.v1';

export type Text = Sample;
export type Mode = 'first-letter' | 'typing' | 'bionic' | 'rsvp';

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

// --- Preferences ---
export type Prefs = {
  themeName?: 'sepia' | 'dark' | 'light';
  fontScale?: number;
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

// --- Progress (resume where you left off) ---
export type Progress = {
  textId: string;
  mode: Mode;
  page?: number;
  pos?: number;          // typing char position
  wordIdx?: number;      // rsvp word index
  revealed?: Record<string, true>;  // first-letter revealed map
  allRevealed?: boolean;
  updatedAt: number;
};

type ProgressMap = Record<string, Progress>; // key: `${textId}:${mode}`

async function loadProgressMap(): Promise<ProgressMap> {
  try {
    const raw = await AsyncStorage.getItem(KEY_PROGRESS);
    return raw ? JSON.parse(raw) : {};
  } catch { return {}; }
}

async function saveProgressMap(map: ProgressMap): Promise<void> {
  try {
    await AsyncStorage.setItem(KEY_PROGRESS, JSON.stringify(map));
  } catch {}
}

export async function saveProgress(textId: string, mode: Mode, data: Partial<Progress>): Promise<void> {
  const map = await loadProgressMap();
  const key = `${textId}:${mode}`;
  map[key] = {
    ...(map[key] || { textId, mode }),
    ...data,
    textId,
    mode,
    updatedAt: Date.now(),
  };
  await saveProgressMap(map);
}

export async function loadProgress(textId: string, mode: Mode): Promise<Progress | null> {
  const map = await loadProgressMap();
  return map[`${textId}:${mode}`] || null;
}

export async function getRecentProgressForText(textId: string): Promise<Progress | null> {
  const map = await loadProgressMap();
  const matches = Object.values(map).filter((p) => p.textId === textId);
  if (!matches.length) return null;
  return matches.sort((a, b) => b.updatedAt - a.updatedAt)[0];
}

export async function clearProgress(textId: string, mode: Mode): Promise<void> {
  const map = await loadProgressMap();
  delete map[`${textId}:${mode}`];
  await saveProgressMap(map);
}

// --- Bookmarks ---
export type Bookmark = {
  id: string;
  textId: string;
  mode: Mode;
  page: number;
  lineIdx?: number;
  preview: string;
  createdAt: number;
};

export async function loadBookmarks(): Promise<Bookmark[]> {
  try {
    const raw = await AsyncStorage.getItem(KEY_BOOKMARKS);
    return raw ? JSON.parse(raw) : [];
  } catch { return []; }
}

export async function addBookmark(b: Omit<Bookmark, 'id' | 'createdAt'>): Promise<Bookmark> {
  const all = await loadBookmarks();
  const bookmark: Bookmark = {
    ...b,
    id: Math.random().toString(36).slice(2, 10) + Date.now().toString(36),
    createdAt: Date.now(),
  };
  all.push(bookmark);
  await AsyncStorage.setItem(KEY_BOOKMARKS, JSON.stringify(all));
  return bookmark;
}

export async function removeBookmark(id: string): Promise<void> {
  const all = await loadBookmarks();
  await AsyncStorage.setItem(KEY_BOOKMARKS, JSON.stringify(all.filter((b) => b.id !== id)));
}

export async function getBookmarksForText(textId: string): Promise<Bookmark[]> {
  const all = await loadBookmarks();
  return all.filter((b) => b.textId === textId).sort((a, b) => a.page - b.page);
}
