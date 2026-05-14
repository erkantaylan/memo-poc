import { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
  Alert,
} from 'react-native';
import { useRouter, useFocusEffect } from 'expo-router';
import { colors } from '@/theme/colors';
import {
  loadAllTexts,
  addCustomText,
  removeCustomText,
  getRecentProgressForText,
  loadBookmarks,
  removeBookmark,
  Text as TextItem,
  Mode,
  Progress,
  Bookmark,
} from '@/data/storage';
import { countLines, countWords } from '@/data/tokenize';

const MODES: { key: Mode; label: string; diff: string; diffColor: string }[] = [
  { key: 'first-letter', label: 'First letters', diff: 'MEDIUM',     diffColor: '#2a3f5e' },
  { key: 'typing',       label: 'Type it out',   diff: 'IMPOSSIBLE', diffColor: '#5a2424' },
  { key: 'bionic',       label: 'Bionic read',   diff: 'AID',        diffColor: '#3a3a3a' },
  { key: 'rsvp',         label: 'Speed read',    diff: 'AID',        diffColor: '#3a3a3a' },
];

const MODE_LABEL: Record<Mode, string> = {
  'first-letter': 'First letters',
  'typing': 'Type it out',
  'bionic': 'Bionic read',
  'rsvp': 'Speed read',
};

function progressSummary(p: Progress): string {
  if (p.mode === 'rsvp' && p.wordIdx !== undefined) return `word ${p.wordIdx + 1}`;
  if (p.mode === 'typing' && p.pos !== undefined) return `position ${p.pos}`;
  if (p.page !== undefined) return `page ${p.page + 1}`;
  return '';
}

export default function HomeScreen() {
  const router = useRouter();
  const [texts, setTexts] = useState<TextItem[]>([]);
  const [progressByText, setProgressByText] = useState<Record<string, Progress>>({});
  const [bookmarksByText, setBookmarksByText] = useState<Record<string, Bookmark[]>>({});
  const [showAdd, setShowAdd] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newBody, setNewBody] = useState('');

  const refresh = useCallback(async () => {
    const ts = await loadAllTexts();
    setTexts(ts);

    const progEntries: Array<[string, Progress]> = [];
    for (const t of ts) {
      const p = await getRecentProgressForText(t.id);
      if (p) progEntries.push([t.id, p]);
    }
    setProgressByText(Object.fromEntries(progEntries));

    const allBookmarks = await loadBookmarks();
    const byText: Record<string, Bookmark[]> = {};
    for (const b of allBookmarks) {
      if (!byText[b.textId]) byText[b.textId] = [];
      byText[b.textId].push(b);
    }
    for (const id of Object.keys(byText)) {
      byText[id].sort((a, b) => a.page - b.page);
    }
    setBookmarksByText(byText);
  }, []);

  useFocusEffect(useCallback(() => { refresh(); }, [refresh]));
  useEffect(() => { refresh(); }, [refresh]);

  const onAdd = async () => {
    if (!newTitle.trim() || !newBody.trim()) {
      Alert.alert('Both title and body required.');
      return;
    }
    await addCustomText(newTitle, newBody);
    setNewTitle('');
    setNewBody('');
    setShowAdd(false);
    refresh();
  };

  const onDelete = (id: string, title: string) => {
    Alert.alert('Delete?', `Remove "${title}"?`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          await removeCustomText(id);
          refresh();
        },
      },
    ]);
  };

  const open = (mode: Mode, textId: string) => {
    router.push(`/${mode}?id=${encodeURIComponent(textId)}`);
  };

  const openContinue = (p: Progress) => {
    const params = new URLSearchParams();
    params.set('id', p.textId);
    if (p.page !== undefined && (p.mode === 'first-letter' || p.mode === 'bionic')) {
      params.set('page', String(p.page));
    } else if (p.mode === 'typing' && p.pos !== undefined) {
      params.set('pos', String(p.pos));
    } else if (p.mode === 'rsvp' && p.wordIdx !== undefined) {
      params.set('wordIdx', String(p.wordIdx));
    }
    router.push(`/${p.mode}?${params.toString()}`);
  };

  const openBookmark = (b: Bookmark) => {
    const params = new URLSearchParams();
    params.set('id', b.textId);
    if (b.mode === 'first-letter' || b.mode === 'bionic') {
      params.set('page', String(b.page));
    }
    router.push(`/${b.mode}?${params.toString()}`);
  };

  const deleteBookmark = (b: Bookmark) => {
    Alert.alert('Remove bookmark?', `"${b.preview}"`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Remove',
        style: 'destructive',
        onPress: async () => {
          await removeBookmark(b.id);
          refresh();
        },
      },
    ]);
  };

  return (
    <ScrollView contentContainerStyle={s.scroll}>
      <Text style={s.subtitle}>Pick a text, then pick a practice mode.</Text>

      {texts.map((t) => {
        const recent = progressByText[t.id];
        const bookmarks = bookmarksByText[t.id] || [];
        return (
          <View key={t.id} style={s.card}>
            <View style={s.cardHead}>
              <Text style={s.cardTitle}>{t.title}</Text>
              {!isSampleId(t.id) && (
                <TouchableOpacity onPress={() => onDelete(t.id, t.title)}>
                  <Text style={s.deleteBtn}>×</Text>
                </TouchableOpacity>
              )}
            </View>
            <Text style={s.meta}>
              {countLines(t.body)} lines · {countWords(t.body).toLocaleString()} words
            </Text>

            {recent && (
              <TouchableOpacity style={s.continueBtn} onPress={() => openContinue(recent)}>
                <Text style={s.continueText}>
                  ↺ Continue: {MODE_LABEL[recent.mode]} · {progressSummary(recent)}
                </Text>
                <Text style={s.continueArrow}>→</Text>
              </TouchableOpacity>
            )}

            {bookmarks.length > 0 && (
              <View style={s.bookmarksSection}>
                <Text style={s.bookmarksTitle}>Bookmarks ({bookmarks.length})</Text>
                {bookmarks.map((b) => (
                  <View key={b.id} style={s.bookmarkRow}>
                    <TouchableOpacity style={s.bookmarkMain} onPress={() => openBookmark(b)}>
                      <Text style={s.bookmarkPreview} numberOfLines={1}>
                        🔖 p{b.page + 1} · {b.preview || `Page ${b.page + 1}`}
                      </Text>
                      <Text style={s.bookmarkMode}>{MODE_LABEL[b.mode]}</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={s.bookmarkDel} onPress={() => deleteBookmark(b)}>
                      <Text style={s.bookmarkDelText}>×</Text>
                    </TouchableOpacity>
                  </View>
                ))}
              </View>
            )}

            <View style={s.modeGrid}>
              {MODES.map((m) => (
                <TouchableOpacity
                  key={m.key}
                  style={s.modeBtn}
                  onPress={() => open(m.key, t.id)}
                  activeOpacity={0.7}
                >
                  <View style={s.modeHead}>
                    <Text style={s.modeName}>{m.label}</Text>
                    <View style={[s.modeDiff, { backgroundColor: m.diffColor }]}>
                      <Text style={s.modeDiffText}>{m.diff}</Text>
                    </View>
                  </View>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        );
      })}

      {!showAdd && (
        <TouchableOpacity style={s.addBtn} onPress={() => setShowAdd(true)}>
          <Text style={s.addBtnText}>+ Add text</Text>
        </TouchableOpacity>
      )}

      {showAdd && (
        <View style={s.form}>
          <TextInput
            style={s.input}
            placeholder="Title"
            placeholderTextColor={colors.muted}
            value={newTitle}
            onChangeText={setNewTitle}
          />
          <TextInput
            style={[s.input, s.textarea]}
            placeholder="Paste the text here..."
            placeholderTextColor={colors.muted}
            value={newBody}
            onChangeText={setNewBody}
            multiline
          />
          <View style={s.formRow}>
            <TouchableOpacity style={s.addBtn} onPress={onAdd}>
              <Text style={s.addBtnText}>Save</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[s.addBtn, s.secondaryBtn]}
              onPress={() => {
                setShowAdd(false);
                setNewTitle('');
                setNewBody('');
              }}
            >
              <Text style={[s.addBtnText, { color: colors.fg }]}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}
      <View style={{ height: 80 }} />
    </ScrollView>
  );
}

function isSampleId(id: string): boolean {
  return id === 'peri-beni' || id === 'hobbit';
}

const s = StyleSheet.create({
  scroll: { padding: 16 },
  subtitle: { color: colors.muted, marginBottom: 16, fontSize: 14 },
  card: {
    padding: 14,
    backgroundColor: colors.cardBg,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 8,
    marginBottom: 12,
  },
  cardHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  cardTitle: { color: colors.fg, fontSize: 18, fontWeight: '500', flex: 1, marginRight: 8 },
  deleteBtn: { color: colors.muted, fontSize: 22, paddingHorizontal: 6 },
  meta: { color: colors.muted, fontSize: 12, marginTop: 4 },
  continueBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 10,
    padding: 10,
    backgroundColor: colors.cardInnerBg,
    borderColor: colors.accent,
    borderWidth: 1,
    borderRadius: 6,
  },
  continueText: { color: colors.fg, fontSize: 13, fontWeight: '600', flex: 1 },
  continueArrow: { color: colors.accent, fontSize: 16, fontWeight: '700' },
  bookmarksSection: {
    marginTop: 10,
    padding: 8,
    backgroundColor: colors.cardInnerBg,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 6,
  },
  bookmarksTitle: { color: colors.muted, fontSize: 11, fontWeight: '700', textTransform: 'uppercase', marginBottom: 6, letterSpacing: 0.5 },
  bookmarkRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
    gap: 4,
  },
  bookmarkMain: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 6,
    backgroundColor: colors.bg,
    borderRadius: 4,
  },
  bookmarkPreview: { color: colors.fg, fontSize: 12, flex: 1, marginRight: 8 },
  bookmarkMode: { color: colors.muted, fontSize: 10, fontWeight: '600' },
  bookmarkDel: {
    width: 28,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.bg,
    borderRadius: 4,
  },
  bookmarkDelText: { color: colors.muted, fontSize: 18, fontWeight: '700' },
  modeGrid: { marginTop: 10, gap: 8 },
  modeBtn: {
    backgroundColor: colors.cardInnerBg,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 6,
    padding: 12,
  },
  modeHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  modeName: { color: colors.fg, fontSize: 15, fontWeight: '600' },
  modeDiff: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 10 },
  modeDiffText: { color: '#fff', fontSize: 10, fontWeight: '700', letterSpacing: 0.3 },
  addBtn: {
    backgroundColor: colors.accent,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 4,
    alignSelf: 'flex-start',
    marginTop: 8,
  },
  secondaryBtn: { backgroundColor: colors.secondaryBg, marginLeft: 8 },
  addBtnText: { color: '#000', fontWeight: '600' },
  form: {
    marginTop: 12,
    padding: 12,
    backgroundColor: colors.cardBg,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 8,
  },
  input: {
    backgroundColor: colors.inputBg,
    color: colors.fg,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 4,
    paddingHorizontal: 10,
    paddingVertical: 8,
    marginBottom: 8,
    fontSize: 15,
  },
  textarea: { minHeight: 140, textAlignVertical: 'top' },
  formRow: { flexDirection: 'row' },
});
