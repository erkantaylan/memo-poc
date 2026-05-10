import { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useLocalSearchParams, Stack } from 'expo-router';
import { colors } from '@/theme/colors';
import { getTextById } from '@/data/storage';
import { tokenize, Tokenized } from '@/data/tokenize';
import { LETTER_REGEX } from '@/data/canonicalize';

export default function FirstLetterScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [tok, setTok] = useState<Tokenized | null>(null);
  const [title, setTitle] = useState('');
  // Track revealed words by their global idx
  const [revealed, setRevealed] = useState<Record<number, boolean>>({});
  const [showDots, setShowDots] = useState(false);

  useEffect(() => {
    (async () => {
      const t = id ? await getTextById(id) : null;
      if (t) {
        setTitle(t.title);
        setTok(tokenize(t.body));
      }
    })();
  }, [id]);

  if (!tok) return <View style={s.container}><Text style={s.muted}>Loading…</Text></View>;

  const revealAll = () => {
    const next: Record<number, boolean> = {};
    tok.lines.forEach((line) => {
      line.tokens.forEach((tk) => {
        if (tk.type === 'token' && tk.isWord) next[tk.idx] = true;
      });
    });
    setRevealed(next);
  };
  const hideAll = () => setRevealed({});

  let total = 0;
  let revealedCount = 0;
  tok.lines.forEach((l) =>
    l.tokens.forEach((tk) => {
      if (tk.type === 'token' && tk.isWord) {
        total++;
        if (revealed[tk.idx]) revealedCount++;
      }
    }),
  );

  return (
    <View style={s.container}>
      <Stack.Screen options={{ title }} />
      <View style={s.bar}>
        <TouchableOpacity style={s.btn} onPress={revealAll}>
          <Text style={s.btnText}>Reveal all</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.btn, s.btnSecondary]} onPress={hideAll}>
          <Text style={[s.btnText, { color: colors.fg }]}>Hide all</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[s.btn, s.btnSecondary]}
          onPress={() => setShowDots((d) => !d)}
        >
          <Text style={[s.btnText, { color: colors.fg }]}>
            {showDots ? 'Show blanks' : 'Show dots'}
          </Text>
        </TouchableOpacity>
        <Text style={s.muted}>
          {revealedCount}/{total}
        </Text>
      </View>
      <ScrollView contentContainerStyle={s.scroll}>
        {tok.lines.map((line, lineIdx) => {
          if (line.isBlank) return <View key={`b-${lineIdx}`} style={s.stanza} />;
          return (
            <Text key={`l-${lineIdx}`} style={s.line}>
              {line.tokens.map((tk) => {
                if (tk.type === 'space') return <Text key={tk.idx}>{tk.text}</Text>;
                if (!tk.isWord) return <Text key={tk.idx} style={s.punct}>{tk.text}</Text>;
                const isRevealed = !!revealed[tk.idx];
                return (
                  <Text
                    key={tk.idx}
                    onPress={() =>
                      setRevealed((r) => ({ ...r, [tk.idx]: !r[tk.idx] }))
                    }
                    style={isRevealed ? s.wordRevealed : s.wordHidden}
                  >
                    {renderFirstLetter(tk.text, isRevealed, showDots)}
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

function renderFirstLetter(text: string, revealed: boolean, dots: boolean): string {
  if (revealed) return text;
  const chars = [...text];
  let i = 0;
  let lead = '';
  while (i < chars.length && !LETTER_REGEX.test(chars[i])) {
    lead += chars[i];
    i++;
  }
  const first = chars[i] || '';
  let j = chars.length - 1;
  let trail = '';
  while (j >= i && !LETTER_REGEX.test(chars[j])) {
    trail = chars[j] + trail;
    j--;
  }
  const rest = chars.slice(i + 1, j + 1);
  const masked = rest
    .map((ch) => (LETTER_REGEX.test(ch) ? (dots ? '·' : '_') : ch))
    .join('');
  return lead + first + masked + trail;
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
    backgroundColor: colors.bg,
  },
  btn: {
    backgroundColor: colors.accent,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 4,
  },
  btnSecondary: {
    backgroundColor: colors.secondaryBg,
    borderColor: colors.border,
    borderWidth: 1,
  },
  btnText: { color: '#000', fontWeight: '600', fontSize: 13 },
  muted: { color: colors.muted, fontSize: 12, marginLeft: 'auto' },
  scroll: { padding: 16, paddingBottom: 40 },
  stanza: { height: 12 },
  line: { color: colors.fg, fontSize: 18, lineHeight: 32, marginBottom: 4 },
  punct: { color: colors.muted },
  wordHidden: {
    color: colors.hint,
    fontWeight: '600',
    backgroundColor: 'rgba(0,0,0,0)',
  },
  wordRevealed: { color: colors.revealed },
});
