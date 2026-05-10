import { useEffect, useState, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useLocalSearchParams, Stack } from 'expo-router';
import { colors } from '@/theme/colors';
import { getTextById } from '@/data/storage';
import { LETTER_REGEX } from '@/data/canonicalize';

type Page = { start: number; end: number };

function paginate(lines: string[], targetWords = 800): Page[] {
  const pages: Page[] = [];
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
  if (start < lines.length) {
    pages.push({ start, end: lines.length - 1 });
  }
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

export default function BionicScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [boldPct, setBoldPct] = useState(50);
  const [page, setPage] = useState(0);

  useEffect(() => {
    (async () => {
      const t = id ? await getTextById(id) : null;
      if (t) {
        setTitle(t.title);
        setBody(t.body);
      }
    })();
  }, [id]);

  const lines = useMemo(() => body.split('\n'), [body]);
  const pages = useMemo(() => paginate(lines, 800), [lines]);

  const visibleLines = useMemo(() => {
    const p = pages[page];
    if (!p) return [];
    return lines.slice(p.start, p.end + 1);
  }, [lines, pages, page]);

  return (
    <View style={s.container}>
      <Stack.Screen options={{ title }} />
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
          <Text
            style={[
              s.btnText,
              { color: colors.fg, opacity: page >= pages.length - 1 ? 0.3 : 1 },
            ]}
          >
            ►
          </Text>
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

      <ScrollView contentContainerStyle={s.scroll}>
        {visibleLines.map((line, i) => {
          if (line.trim() === '') return <View key={`b-${i}`} style={s.stanza} />;
          const parts = line.split(/(\s+)/);
          return (
            <Text key={`l-${i}`} style={s.line}>
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
  scroll: { padding: 16, paddingBottom: 40 },
  stanza: { height: 12 },
  line: { color: colors.fg, fontSize: 17, lineHeight: 30 },
  muted: { color: colors.muted },
  boldPart: { color: colors.fg, fontWeight: '700' },
  restPart: { color: colors.muted },
});
