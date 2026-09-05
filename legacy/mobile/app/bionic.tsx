import { useEffect, useState, useMemo, useRef } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Alert, ToastAndroid, Platform } from 'react-native';
import { useLocalSearchParams, Stack, useRouter } from 'expo-router';
import { colors } from '@/theme/colors';
import { getTextById, loadProgress, saveProgress, addBookmark } from '@/data/storage';
import { LETTER_REGEX } from '@/data/canonicalize';
import { useFontScale } from '@/hooks/useFontScale';

// Count words by whitespace-split (matches RSVP's word splitter)
function countWordsBySpace(text: string): number {
  let count = 0;
  let inWord = false;
  for (let i = 0; i < text.length; i++) {
    const isSpace = /\s/.test(text[i]);
    if (!isSpace && !inWord) { count++; inWord = true; }
    else if (isSpace) inWord = false;
  }
  return count;
}

type PageRange = { start: number; end: number };

function paginate(lines: string[], targetWords = 800): PageRange[] {
  const pages: PageRange[] = [];
  let start = 0;
  let words = 0;
  for (let i = 0; i < lines.length; i++) {
    words += (lines[i].match(/\p{L}+/gu) || []).length;
    const isBlank = lines[i].trim() === '';
    if (words >= targetWords && (isBlank || i === lines.length - 1)) {
      pages.push({ start, end: i });
      start = i + 1;
      words = 0;
    }
  }
  if (start < lines.length) pages.push({ start, end: lines.length - 1 });
  return pages.length ? pages : [{ start: 0, end: lines.length - 1 }];
}

function splitToken(tok: string): { lead: string; core: string; trail: string } {
  const chars = [...tok];
  let i = 0;
  let j = chars.length - 1;
  while (i < chars.length && !LETTER_REGEX.test(chars[i])) i++;
  while (j >= i && !LETTER_REGEX.test(chars[j])) j--;
  if (i > j) return { lead: tok, core: '', trail: '' };
  return {
    lead: chars.slice(0, i).join(''),
    core: chars.slice(i, j + 1).join(''),
    trail: chars.slice(j + 1).join(''),
  };
}

function toast(msg: string) {
  if (Platform.OS === 'android') ToastAndroid.show(msg, ToastAndroid.SHORT);
  else Alert.alert(msg);
}

export default function BionicScreen() {
  const { id, page: paramPage, wordIdx: paramWordIdx } = useLocalSearchParams<{
    id: string; page?: string; wordIdx?: string;
  }>();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [boldPct, setBoldPct] = useState(50);
  const [page, setPage] = useState(0);
  const { scale, increase, decrease } = useFontScale();
  const router = useRouter();
  const scrollRef = useRef<ScrollView>(null);
  const loadedRef = useRef(false);

  useEffect(() => {
    let cancelled = false;
    loadedRef.current = false;
    (async () => {
      const t = id ? await getTextById(id) : null;
      if (!t || cancelled) return;
      setTitle(t.title);
      setBody(t.body);

      // Compute pages here so we can map wordIdx → page on first load.
      const ls = t.body.split('\n');
      const pgs = paginate(ls, 800);

      let startPage = 0;
      if (paramPage !== undefined) {
        startPage = Math.max(0, parseInt(paramPage, 10) || 0);
      } else if (paramWordIdx !== undefined) {
        const targetWord = Math.max(0, parseInt(paramWordIdx, 10) || 0);
        let cumulative = 0;
        startPage = pgs.length - 1;
        for (let p = 0; p < pgs.length; p++) {
          let pageWords = 0;
          for (let i = pgs[p].start; i <= pgs[p].end; i++) {
            pageWords += countWordsBySpace(ls[i]);
          }
          if (cumulative + pageWords > targetWord) { startPage = p; break; }
          cumulative += pageWords;
        }
      } else {
        const prog = await loadProgress(t.id, 'bionic');
        if (prog?.page !== undefined) startPage = prog.page;
      }
      if (!cancelled) {
        setPage(Math.max(0, Math.min(pgs.length - 1, startPage)));
        loadedRef.current = true;
      }
    })();
    return () => { cancelled = true; };
  }, [id, paramPage, paramWordIdx]);

  const lines = useMemo(() => body.split('\n'), [body]);
  const pages = useMemo(() => paginate(lines, 800), [lines]);

  // Clamp page if it's out of bounds after pages loaded
  useEffect(() => {
    if (pages.length > 0 && page >= pages.length) setPage(pages.length - 1);
  }, [pages.length, page]);

  // Scroll to top whenever page changes
  useEffect(() => {
    scrollRef.current?.scrollTo({ y: 0, animated: false });
  }, [page]);

  // Save progress when page changes (after initial load)
  useEffect(() => {
    if (!id || !loadedRef.current) return;
    saveProgress(id, 'bionic', { page });
  }, [id, page]);

  const visibleLines = useMemo(() => {
    const p = pages[page];
    if (!p) return [];
    return lines.slice(p.start, p.end + 1);
  }, [lines, pages, page]);

  const previewForPage = (pIdx: number) => {
    const p = pages[pIdx];
    if (!p) return '';
    const firstLine = lines.slice(p.start, p.end + 1).find((l) => l.trim() !== '') || '';
    return firstLine.slice(0, 40);
  };

  const bookmarkPage = async () => {
    if (!id) return;
    await addBookmark({
      textId: id,
      mode: 'bionic',
      page,
      preview: previewForPage(page) || `Page ${page + 1}`,
    });
    toast(`Bookmarked page ${page + 1}`);
  };

  const switchToRsvp = () => {
    if (!id) return;
    const p = pages[page];
    if (!p) return;
    let wordIdx = 0;
    for (let i = 0; i < p.start; i++) {
      wordIdx += countWordsBySpace(lines[i]);
    }
    router.replace(`/rsvp?id=${encodeURIComponent(id)}&wordIdx=${wordIdx}`);
  };

  const bookmarkLine = async (lineText: string, lineOffsetInPage: number) => {
    if (!id) return;
    const p = pages[page];
    await addBookmark({
      textId: id,
      mode: 'bionic',
      page,
      lineIdx: p ? p.start + lineOffsetInPage : undefined,
      preview: lineText.slice(0, 40) || `Page ${page + 1}`,
    });
    toast(`Bookmarked: "${lineText.slice(0, 24)}…"`);
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
        <TouchableOpacity
          style={[s.btn, s.btnSecondary]}
          disabled={page === 0}
          onPress={() => setPage((p) => Math.max(0, p - 1))}
        >
          <Text style={[s.btnText, { color: colors.fg, opacity: page === 0 ? 0.3 : 1 }]}>◄</Text>
        </TouchableOpacity>
        <View style={s.pageInfo}>
          <Text style={s.pageInfoText}>
            Page {page + 1} / {pages.length}
          </Text>
        </View>
        <TouchableOpacity
          style={[s.btn, s.btnSecondary]}
          disabled={page >= pages.length - 1}
          onPress={() => setPage((p) => Math.min(pages.length - 1, p + 1))}
        >
          <Text style={[s.btnText, { color: colors.fg, opacity: page >= pages.length - 1 ? 0.3 : 1 }]}>►</Text>
        </TouchableOpacity>
        <TouchableOpacity style={s.bookmarkBtn} onPress={bookmarkPage}>
          <Text style={s.bookmarkBtnText}>🔖</Text>
        </TouchableOpacity>
        <TouchableOpacity style={s.switchBtn} onPress={switchToRsvp}>
          <Text style={s.switchBtnText}>↔ Speed</Text>
        </TouchableOpacity>
        <View style={s.boldGroup}>
          <Text style={s.boldLabel}>Bold</Text>
          {[30, 50, 70].map((pct) => (
            <TouchableOpacity
              key={pct}
              onPress={() => setBoldPct(pct)}
              style={[s.boldOpt, boldPct === pct && s.boldOptActive]}
            >
              <Text style={[s.boldOptText, boldPct === pct && { color: '#000' }]}>{pct}%</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      <ScrollView ref={scrollRef} contentContainerStyle={s.scroll}>
        {visibleLines.map((line, i) => {
          if (line.trim() === '') return <View key={`b-${i}`} style={s.stanza} />;
          const parts = line.split(/(\s+)/);
          return (
            <Text
              key={`l-${i}`}
              style={[s.line, { fontSize: 17 * scale, lineHeight: 30 * scale }]}
              onLongPress={() => bookmarkLine(line, i)}
            >
              {parts.map((p, j) => {
                if (/^\s+$/.test(p)) return <Text key={j}>{p}</Text>;
                if (!LETTER_REGEX.test(p)) return <Text key={j} style={s.muted}>{p}</Text>;
                const { lead, core, trail } = splitToken(p);
                const coreChars = [...core];
                const boldLen = Math.max(1, Math.ceil((coreChars.length * boldPct) / 100));
                const bold = coreChars.slice(0, boldLen).join('');
                const rest = coreChars.slice(boldLen).join('');
                return (
                  <Text key={j}>
                    {lead ? <Text style={s.muted}>{lead}</Text> : null}
                    <Text style={s.boldPart}>{bold}</Text>
                    {rest ? <Text style={s.restPart}>{rest}</Text> : null}
                    {trail ? <Text style={s.muted}>{trail}</Text> : null}
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

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 8,
    gap: 8,
    borderBottomColor: colors.border,
    borderBottomWidth: 1,
    flexWrap: 'wrap',
  },
  btn: {
    backgroundColor: colors.secondaryBg,
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 4,
    borderColor: colors.border,
    borderWidth: 1,
  },
  btnSecondary: {},
  btnText: { fontWeight: '600', fontSize: 13 },
  pageInfo: { paddingHorizontal: 8 },
  pageInfoText: { color: colors.fg, fontWeight: '600', fontSize: 13 },
  bookmarkBtn: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    backgroundColor: colors.cardInnerBg,
    borderColor: colors.border,
    borderWidth: 1,
  },
  bookmarkBtnText: { fontSize: 14 },
  switchBtn: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    backgroundColor: colors.cardInnerBg,
    borderColor: colors.border,
    borderWidth: 1,
  },
  switchBtnText: { color: colors.fg, fontSize: 11, fontWeight: '700' },
  boldGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    marginLeft: 'auto',
    gap: 4,
  },
  boldLabel: { color: colors.muted, fontSize: 12, marginRight: 4 },
  boldOpt: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    backgroundColor: colors.cardInnerBg,
  },
  boldOptActive: { backgroundColor: colors.accent },
  boldOptText: { color: colors.fg, fontSize: 11, fontWeight: '600' },
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
  scroll: { padding: 16, paddingBottom: 40 },
  stanza: { height: 12 },
  line: { color: colors.fg, fontSize: 17, lineHeight: 30, marginBottom: 10 },
  muted: { color: colors.muted },
  boldPart: { color: colors.fg, fontWeight: '700' },
  restPart: { color: colors.muted },
});
