import { useEffect, useState, useRef, useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, useWindowDimensions } from 'react-native';
import { useLocalSearchParams, Stack, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors } from '@/theme/colors';
import { getTextById, loadProgress, saveProgress } from '@/data/storage';
import { useFontScale } from '@/hooks/useFontScale';

const WPM_OPTIONS = [200, 300, 400, 500, 700];

export default function RsvpScreen() {
  const { id, wordIdx: paramWordIdx } = useLocalSearchParams<{ id: string; wordIdx?: string }>();
  const [title, setTitle] = useState('');
  const [words, setWords] = useState<string[]>([]);
  const [wordIdx, setWordIdx] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [wpm, setWpm] = useState(300);
  const { scale, increase, decrease } = useFontScale();
  const insets = useSafeAreaInsets();
  const { width: winWidth } = useWindowDimensions();
  const router = useRouter();
  const loadedRef = useRef(false);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const switchToBionic = () => {
    if (!id) return;
    setPlaying(false);
    router.replace(`/bionic?id=${encodeURIComponent(id)}&wordIdx=${wordIdx}`);
  };

  useEffect(() => {
    let cancelled = false;
    setWords([]);
    setWordIdx(0);
    loadedRef.current = false;
    (async () => {
      const t = id ? await getTextById(id) : null;
      if (!t || cancelled) return;
      setTitle(t.title);
      const w = t.body.split(/\s+/).filter((s) => s.length > 0);
      setWords(w);

      let startIdx = 0;
      if (paramWordIdx !== undefined) {
        startIdx = Math.max(0, Math.min(w.length - 1, parseInt(paramWordIdx, 10) || 0));
      } else {
        const prog = await loadProgress(t.id, 'rsvp');
        if (prog?.wordIdx !== undefined && prog.wordIdx < w.length) {
          startIdx = prog.wordIdx;
        }
      }
      if (!cancelled) {
        setWordIdx(startIdx);
        loadedRef.current = true;
      }
    })();
    return () => { cancelled = true; };
  }, [id, paramWordIdx]);

  // Debounced save when wordIdx changes
  useEffect(() => {
    if (!id || !loadedRef.current) return;
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      saveProgress(id, 'rsvp', { wordIdx });
    }, 500);
    return () => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    };
  }, [id, wordIdx]);

  // Per-word timer — each word's display duration scales with its length
  // and gets a longer hold on sentence-ending punctuation.
  useEffect(() => {
    if (!playing || words.length === 0) return;
    if (wordIdx >= words.length - 1) {
      setPlaying(false);
      return;
    }
    const word = words[wordIdx];
    const len = [...word].length;
    const base = 60000 / wpm;
    // +6% per char beyond 5; e.g. 10-char word = 1.30x, 15-char = 1.60x
    const lengthFactor = 1 + Math.max(0, len - 5) * 0.06;
    // Sentence-ending punctuation gets a 1.5x pause
    const endsSentence = /[.!?…]["')\]]?$/.test(word);
    // Comma / semicolon / colon = 1.2x
    const endsClause = /[,;:]["')\]]?$/.test(word);
    const punctFactor = endsSentence ? 1.5 : endsClause ? 1.2 : 1;
    const delay = Math.max(50, base * lengthFactor * punctFactor);

    const t = setTimeout(() => setWordIdx((i) => i + 1), delay);
    return () => clearTimeout(t);
  }, [playing, wpm, wordIdx, words]);

  const currentWord = words[wordIdx] || '';

  // Identify "anchor" character (~30% mark) like RSVP standards
  const anchor = useMemo(() => {
    const len = [...currentWord].length;
    return Math.max(0, Math.floor((len - 1) * 0.3));
  }, [currentWord]);

  // Auto-shrink font so long words fit on one line.
  // Estimate: average monospaced glyph ≈ 0.62 em wide.
  const fontSize = useMemo(() => {
    const len = Math.max(1, [...currentWord].length);
    const base = 44 * scale;
    const available = winWidth - 48; // padding + safety
    const maxByWidth = available / (len * 0.62);
    return Math.max(16, Math.min(base, maxByWidth));
  }, [currentWord, scale, winWidth]);

  const togglePlay = () => setPlaying((p) => !p);
  const restart = () => { setWordIdx(0); setPlaying(false); };
  const prev = () => setWordIdx((i) => Math.max(0, i - 1));
  const next = () => setWordIdx((i) => Math.min(words.length - 1, i + 1));

  if (words.length === 0) {
    return <View style={s.container}><Text style={s.muted}>Loading…</Text></View>;
  }

  const chars = [...currentWord];

  return (
    <View style={s.container}>
      <Stack.Screen
        options={{
          title,
          headerRight: () => (
            <View style={s.headerRight}>
              <TouchableOpacity style={s.fontBtn} onPress={decrease}>
                <Text style={s.fontBtnText}>A−</Text>
              </TouchableOpacity>
              <TouchableOpacity style={s.fontBtn} onPress={increase}>
                <Text style={s.fontBtnText}>A+</Text>
              </TouchableOpacity>
            </View>
          ),
        }}
      />

      <View style={s.bar}>
        <Text style={s.wpmLabel}>WPM</Text>
        {WPM_OPTIONS.map((w) => (
          <TouchableOpacity
            key={w}
            style={[s.wpmBtn, wpm === w && s.wpmBtnActive]}
            onPress={() => setWpm(w)}
          >
            <Text style={[s.wpmBtnText, wpm === w && { color: '#000' }]}>{w}</Text>
          </TouchableOpacity>
        ))}
        <TouchableOpacity style={s.switchBtn} onPress={switchToBionic}>
          <Text style={s.switchBtnText}>↔ Read</Text>
        </TouchableOpacity>
        <Text style={s.stats}>{wordIdx + 1}/{words.length}</Text>
      </View>

      <View style={s.progress}>
        <View
          style={[
            s.progressFill,
            { width: `${words.length ? ((wordIdx + 1) / words.length) * 100 : 0}%` },
          ]}
        />
      </View>

      <TouchableOpacity activeOpacity={1} style={s.wordContainer} onPress={togglePlay}>
        <Text style={[s.word, { fontSize }]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.5}>
          {chars.map((c, i) => (
            <Text key={i} style={i === anchor ? s.anchor : undefined}>{c}</Text>
          ))}
        </Text>
      </TouchableOpacity>

      <View style={[s.controls, { paddingBottom: Math.max(insets.bottom, 80) }]}>
        <TouchableOpacity style={[s.ctrlBtn, s.ctrlBtnSec]} onPress={restart}>
          <Text style={[s.ctrlBtnText, { color: colors.fg }]}>↺</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.ctrlBtn, s.ctrlBtnSec]} onPress={prev}>
          <Text style={[s.ctrlBtnText, { color: colors.fg }]}>◄</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.ctrlBtn, s.ctrlBtnPlay]} onPress={togglePlay}>
          <Text style={s.ctrlPlayText}>{playing ? '❚❚' : '▶'}</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.ctrlBtn, s.ctrlBtnSec]} onPress={next}>
          <Text style={[s.ctrlBtnText, { color: colors.fg }]}>►</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  headerRight: { flexDirection: 'row', gap: 8, marginRight: 4 },
  fontBtn: {
    backgroundColor: colors.cardInnerBg,
    borderColor: colors.border,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 4,
  },
  fontBtnText: { color: colors.fg, fontSize: 13, fontWeight: '700' },
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 8,
    gap: 4,
    borderBottomColor: colors.border,
    borderBottomWidth: 1,
    flexWrap: 'wrap',
  },
  wpmLabel: { color: colors.muted, fontSize: 12, marginRight: 4 },
  wpmBtn: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    backgroundColor: colors.cardInnerBg,
  },
  wpmBtnActive: { backgroundColor: colors.accent },
  wpmBtnText: { color: colors.fg, fontSize: 11, fontWeight: '600' },
  switchBtn: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 4,
    backgroundColor: colors.cardInnerBg,
    borderColor: colors.border,
    borderWidth: 1,
    marginLeft: 4,
  },
  switchBtnText: { color: colors.fg, fontSize: 11, fontWeight: '700' },
  stats: { color: colors.muted, fontSize: 12, marginLeft: 'auto' },
  progress: { height: 3, backgroundColor: colors.cardInnerBg },
  progressFill: { height: '100%', backgroundColor: colors.revealed },
  wordContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 16,
  },
  word: { color: colors.fg, fontWeight: '600', textAlign: 'center' },
  anchor: { color: colors.accent, fontWeight: '700' },
  controls: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 14,
    paddingHorizontal: 16,
    paddingTop: 16,
    borderTopColor: colors.border,
    borderTopWidth: 1,
  },
  ctrlBtn: {
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ctrlBtnSec: { backgroundColor: colors.secondaryBg, borderColor: colors.border, borderWidth: 1 },
  ctrlBtnPlay: { backgroundColor: colors.accent, width: 72, height: 72, borderRadius: 36 },
  ctrlBtnText: { fontSize: 20, fontWeight: '700' },
  ctrlPlayText: { fontSize: 22, fontWeight: '700', color: '#000' },
  muted: { color: colors.muted, fontSize: 14, padding: 16 },
});
