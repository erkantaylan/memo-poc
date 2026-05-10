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
  Text as TextItem,
} from '@/data/storage';
import { countLines, countWords } from '@/data/tokenize';

type ModeKey = 'first-letter' | 'typing' | 'bionic';

const MODES: { key: ModeKey; label: string; diff: string; diffColor: string }[] = [
  { key: 'first-letter', label: 'First letters', diff: 'MEDIUM',     diffColor: '#2a3f5e' },
  { key: 'typing',       label: 'Type it out',   diff: 'IMPOSSIBLE', diffColor: '#5a2424' },
  { key: 'bionic',       label: 'Bionic read',   diff: 'AID',        diffColor: '#3a3a3a' },
];

export default function HomeScreen() {
  const router = useRouter();
  const [texts, setTexts] = useState<TextItem[]>([]);
  const [showAdd, setShowAdd] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newBody, setNewBody] = useState('');

  const refresh = useCallback(async () => {
    setTexts(await loadAllTexts());
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

  const open = (mode: ModeKey, textId: string) => {
    router.push(`/${mode}?id=${encodeURIComponent(textId)}`);
  };

  return (
    <ScrollView contentContainerStyle={s.scroll}>
      <Text style={s.subtitle}>Pick a text, then pick a practice mode.</Text>

      {texts.map((t) => (
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
      ))}

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
    </ScrollView>
  );
}

function isSampleId(id: string): boolean {
  return id === 'peri-beni' || id === 'hobbit';
}

const s = StyleSheet.create({
  scroll: { padding: 16, paddingBottom: 48 },
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
