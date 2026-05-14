import { useEffect, useState, useMemo, useRef } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Alert, ToastAndroid, Platform } from 'react-native';
import { useLocalSearchParams, Stack } from 'expo-router';
import { colors } from '@/theme/colors';
import { getTextById, loadProgress, saveProgress, addBookmark } from '@/data/storage';
import { LETTER_REGEX } from '@/data/canonicalize';
import { useFontScale } from '@/hooks/useFontScale';

type PageRange = { start: number; end: number };

type LineToken =
  | { type: 'word'; text: string; lineIdx: number; wordPos: number }
  | { type: 'punct'; text: string }
  | { type: 'space'; text: string };

type LineData = { tokens: LineToken[]; isBlank: boolean; lineIdx: number; raw: string };

function paginateRaw(rawLines: string[], targetWords = 350): PageRange[] {
  const pages: PageRange[] = [];
  let start = 0;
  let words = 0;
  for (let i = 0; i < rawLines.length; i++) {
    words += (rawLines[i].match(/\p{L}+/gu) || []).length;
    if (words >= targetWords && (rawLines[i].trim() === '' || i === rawLines.length - 1)) {
      pages.push({ start, end: i });
      start = i + 1;
      words = 0;
    }
  }
  if (start < rawLines.length) pages.push({ start, end: rawLines.length - 1 });
  return pages.length ? pages : [{ start: 0, end: rawLines.length - 1 }];
}

function tokenizePageLines(rawLines: string[], startLineIdx: number): LineData[] {
  return rawLines.map((raw, i) => {
    const lineIdx = startLineIdx + i;
    if (raw.trim() === '') return { tokens: [], isBlank: true, lineIdx, raw };
    const parts = raw.split(/(\s+)/);
    const tokens: LineToken[] = [];
    let wordPos = 0;
    for (const p of parts) {
      if (p === '') continue;
      if (/^\s+$/.test(p)) {
        tokens.push({ type: 'space', text: p });
      } else if (LETTER_REGEX.test(p)) {
        tokens.push({ type: 'word', text: p, lineIdx, wordPos: wordPos++ });
      } else {
        tokens.push({ type: 'punct', text: p });
        wordPos++;
      }
    }
    return { tokens, isBlank: false, lineIdx, raw };
  });
}

function toast(msg: string) {
  if (Platform.OS === 'android') ToastAndroid.show(msg, ToastAndroid.SHORT);
  else Alert.alert(msg);
}

export default function FirstLetterScreen() {
  const { id, page: paramPage } = useLocalSearchParams<{ id: string; page?: string }>();
  const [rawLines, setRawLines] = useState<string[]>([]);
  const [pages, setPages] = useState<PageRange[]>([]);
  const [title, setTitle] = useState('');
  const [revealMap, setRevealMap] = useState<Record<string, true>>({});
  const [allRevealed, setAllRevealed] = useState(false);
  const [showDots, setShowDots] = useState(false);
  const [page, setPage] = useState(0);
  const { scale, increase, decrease } = useFontScale();
  const scrollRef = useRef<ScrollView>(null);
  const loadedRef = useRef(false);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let cancelled = false;
    setRawLines([]);
    setPages([]);
    setPage(0);
    setRevealMap({});
    setAllRevealed(false);
    loadedRef.current = false;
    (async () => {
      const t = id ? await getTextById(id) : null;
      if (!t || cancelled) return;
      setTitle(t.title);
      const lines = t.body.split('\n');
      const pgs = paginateRaw(lines);
      setRawLines(lines);
      setPages(pgs);

      let startPage = 0;
      if (paramPage !== undefined) {
        startPage = Math.max(0, Math.min(pgs.length - 1, parseInt(paramPage, 10) || 0));
      } else {
        const prog = await loadProgress(t.id, 'first-letter');
        if (prog) {
          if (prog.page !== undefined) startPage = Math.min(pgs.length - 1, prog.page);
          if (prog.revealed) setRevealMap(prog.revealed);
          if (prog.allRevealed) setAllRevealed(true);
        }
      }
      if (!cancelled) {
        setPage(startPage);
        loadedRef.current = true;
      }
    })();
    return () => { cancelled = true; };
  }, [id, paramPage]);

  // Scroll to top whenever page changes
  useEffect(() => {
    scrollRef.current?.scrollTo({ y: 0, animated: false });
  }, [page]);

  // Debounced save of progress
  useEffect(() => {
    if (!id || !loadedRef.current) return;
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      saveProgress(id, 'first-letter', { page, revealed: revealMap, allRevealed });
    }, 500);
    return () => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    };
  }, [id, page, revealMap, allRevealed]);

  const visibleLines = useMemo(() => {
    if (!rawLines.length || !pages[page]) return [];
    const { start, end } = pages[page];
    return tokenizePageLines(rawLines.slice(start, end + 1), start);
  }, [rawLines, pages, page]);

  const total = useMemo(
    () => rawLines.reduce((n, l) => n + ((l.match(/\p{L}+/gu) || []).length), 0),
    [rawLines],
  );

  const revealedCount = allRevealed ? total : Object.keys(revealMap).length;

  if (!rawLines.length) return <View style={s.container}><Text style={s.muted}>Loading…</Text></View>;

  const revealAll = () => setAllRevealed(true);
  const hideAll = () => { setAllRevealed(false); setRevealMap({}); };

  const toggleWord = (lineIdx: number, wordPos: number) => {
    if (allRevealed) return;
    const key = `${lineIdx}:${wordPos}` as const;
    setRevealMap((r) => {
      const next = { ...r } as Record<string, true>;
      if (next[key]) delete next[key];
      else next[key] = true;
      return next;
    });
  };

  const previewForPage = (pIdx: number) => {
    const p = pages[pIdx];
    if (!p) return '';
    const firstLine = rawLines.slice(p.start, p.end + 1).find((l) => l.trim() !== '') || '';
    return firstLine.slice(0, 40);
  };

  const bookmarkPage = async () => {
    if (!id) return;
    await addBookmark({
      textId: id,
      mode: 'first-letter',
      page,
      preview: previewForPage(page) || `Page ${page + 1}`,
    });
    toast(`Bookmarked page ${page + 1}`);
  };

  const bookmarkLine = async (line: LineData) => {
    if (!id) return;
    await addBookmark({
      textId: id,
      mode: 'first-letter',
      page,
      lineIdx: line.lineIdx,
      preview: line.raw.slice(0, 40) || `Page ${page + 1}`,
    });
    toast(`Bookmarked: "${line.raw.slice(0, 24)}…"`);
  };

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
        <TouchableOpacity style={s.btn} onPress={revealAll}>
          <Text style={s.btnText}>Reveal all</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.btn, s.btnSecondary]} onPress={hideAll}>
          <Text style={[s.btnText, { color: colors.fg }]}>Hide all</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.btn, s.btnSecondary]} onPress={() => setShowDots((d) => !d)}>
          <Text style={[s.btnText, { color: colors.fg }]}>{showDots ? 'Blanks' : 'Dots'}</Text>
        </TouchableOpacity>
        <Text style={s.muted}>{revealedCount}/{total}</Text>
      </View>

      <View style={s.pageBar}>
        <TouchableOpacity
          style={[s.pageBtn, page === 0 && s.pageBtnDisabled]}
          disabled={page === 0}
          onPress={() => setPage((p) => p - 1)}
        >
          <Text style={[s.pageBtnText, page === 0 && { opacity: 0.3 }]}>◄</Text>
        </TouchableOpacity>
        <Text style={s.pageInfo}>Page {page + 1} / {pages.length}</Text>
        <TouchableOpacity
          style={[s.pageBtn, page >= pages.length - 1 && s.pageBtnDisabled]}
          disabled={page >= pages.length - 1}
          onPress={() => setPage((p) => p + 1)}
        >
          <Text style={[s.pageBtnText, page >= pages.length - 1 && { opacity: 0.3 }]}>►</Text>
        </TouchableOpacity>
        <TouchableOpacity style={s.bookmarkBtn} onPress={bookmarkPage}>
          <Text style={s.bookmarkBtnText}>🔖</Text>
        </TouchableOpacity>
      </View>

      <ScrollView ref={scrollRef} contentContainerStyle={s.scroll}>
        {visibleLines.map((line) => {
          if (line.isBlank) return <View key={`b-${line.lineIdx}`} style={s.stanza} />;
          return (
            <Text
              key={`l-${line.lineIdx}`}
              style={[s.line, { fontSize: 18 * scale, lineHeight: 32 * scale }]}
              onLongPress={() => bookmarkLine(line)}
            >
              {line.tokens.map((tk, ti) => {
                if (tk.type === 'space') return <Text key={ti}>{tk.text}</Text>;
                if (tk.type === 'punct') return <Text key={ti} style={s.punct}>{tk.text}</Text>;
                const key = `${tk.lineIdx}:${tk.wordPos}`;
                const isRev = allRevealed || !!revealMap[key];
                return (
                  <Text
                    key={ti}
                    onPress={() => toggleWord(tk.lineIdx, tk.wordPos)}
                    style={isRev ? s.wordRevealed : s.wordHidden}
                  >
                    {renderFirstLetter(tk.text, isRev, showDots)}
                  </Text>
                );
              })}
            </Text>
          );
        })}
        <View style={{ height: 80 }} />
      </ScrollView>
    </View>
  );
}

function renderFirstLetter(text: string, revealed: boolean, dots: boolean): string {
  if (revealed) return text;
  const chars = [...text];
  let i = 0;
  while (i < chars.length && !LETTER_REGEX.test(chars[i])) i++;
  const first = chars[i] ?? '';
  let j = chars.length - 1;
  while (j > i && !LETTER_REGEX.test(chars[j])) j--;
  const rest = chars.slice(i + 1, j + 1).map((ch) =>
    LETTER_REGEX.test(ch) ? (dots ? '·' : '_') : ch,
  );
  return chars.slice(0, i).join('') + first + rest.join('') + chars.slice(j + 1).join('');
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
    gap: 6,
    borderBottomColor: colors.border,
    borderBottomWidth: 1,
    backgroundColor: colors.bg,
    flexWrap: 'wrap',
  },
  pageBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 12,
    paddingVertical: 6,
    gap: 12,
    borderBottomColor: colors.border,
    borderBottomWidth: 1,
  },
  pageBtn: {
    backgroundColor: colors.secondaryBg,
    borderColor: colors.border,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 4,
    borderRadius: 4,
  },
  pageBtnDisabled: { opacity: 0.4 },
  pageBtnText: { color: colors.fg, fontWeight: '600', fontSize: 13 },
  pageInfo: { color: colors.fg, fontWeight: '600', fontSize: 13 },
  bookmarkBtn: {
    backgroundColor: colors.cardInnerBg,
    borderColor: colors.border,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderRadius: 4,
  },
  bookmarkBtnText: { fontSize: 14 },
  btn: { backgroundColor: colors.accent, paddingHorizontal: 10, paddingVertical: 6, borderRadius: 4 },
  btnSecondary: { backgroundColor: colors.secondaryBg, borderColor: colors.border, borderWidth: 1 },
  btnText: { color: '#000', fontWeight: '600', fontSize: 12 },
  muted: { color: colors.muted, fontSize: 12, marginLeft: 'auto' },
  scroll: { padding: 16 },
  stanza: { height: 12 },
  line: { color: colors.fg, marginBottom: 10 },
  punct: { color: colors.muted },
  wordHidden: { color: colors.hint, fontWeight: '600' },
  wordRevealed: { color: colors.revealed },
});
