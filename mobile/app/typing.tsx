import { useEffect, useMemo, useRef, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
  Keyboard,
} from 'react-native';
import { useLocalSearchParams, Stack } from 'expo-router';
import { colors } from '@/theme/colors';
import { getTextById } from '@/data/storage';
import { canon, isLetter } from '@/data/canonicalize';

export default function TypingScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [title, setTitle] = useState('');
  const [chars, setChars] = useState<string[]>([]);
  const [pos, setPos] = useState(0);
  const [typedFlag, setTypedFlag] = useState<boolean[]>([]);
  const [mistakes, setMistakes] = useState(0);
  const [done, setDone] = useState(false);
  const inputRef = useRef<TextInput>(null);
  const scrollRef = useRef<ScrollView>(null);

  useEffect(() => {
    (async () => {
      const t = id ? await getTextById(id) : null;
      if (t) {
        setTitle(t.title);
        const ch = [...t.body];
        setChars(ch);
        setTypedFlag(new Array(ch.length).fill(false));
        // skip leading non-letters
        let p = 0;
        while (p < ch.length && !isLetter(ch[p])) p++;
        setPos(p);
      }
    })();
  }, [id]);

  const reset = () => {
    setTypedFlag(new Array(chars.length).fill(false));
    let p = 0;
    while (p < chars.length && !isLetter(chars[p])) p++;
    setPos(p);
    setMistakes(0);
    setDone(false);
    inputRef.current?.focus();
  };

  // Letter counts
  const { totalLetters, doneLetters } = useMemo(() => {
    let t = 0;
    let d = 0;
    for (let i = 0; i < chars.length; i++) {
      if (isLetter(chars[i])) {
        t++;
        if (typedFlag[i]) d++;
      }
    }
    return { totalLetters: t, doneLetters: d };
  }, [chars, typedFlag]);

  const onInputChange = (text: string) => {
    if (text.length === 0) return;
    // Read the last char the user just typed
    const last = text[text.length - 1];
    if (last === '\n') return;
    if (!isLetter(last)) {
      // ignore non-letters; clear input
      inputRef.current?.clear();
      return;
    }
    if (pos >= chars.length) return;
    if (canon(last) === canon(chars[pos])) {
      const nextFlag = typedFlag.slice();
      nextFlag[pos] = true;
      let next = pos + 1;
      while (next < chars.length && !isLetter(chars[next])) next++;
      setTypedFlag(nextFlag);
      setPos(next);
      if (next >= chars.length) setDone(true);
    } else {
      setMistakes((m) => m + 1);
    }
    inputRef.current?.clear();
  };

  const revealLetter = () => {
    if (pos >= chars.length || !isLetter(chars[pos])) return;
    setMistakes((m) => m + 1);
    const nextFlag = typedFlag.slice();
    nextFlag[pos] = true;
    let next = pos + 1;
    while (next < chars.length && !isLetter(chars[next])) next++;
    setTypedFlag(nextFlag);
    setPos(next);
    if (next >= chars.length) setDone(true);
  };

  const revealWord = () => {
    if (pos >= chars.length) return;
    setMistakes((m) => m + 1);
    const nextFlag = typedFlag.slice();
    let p = pos;
    while (p < chars.length && !/\s/.test(chars[p])) {
      if (isLetter(chars[p])) nextFlag[p] = true;
      p++;
    }
    while (p < chars.length && !isLetter(chars[p])) p++;
    setTypedFlag(nextFlag);
    setPos(p);
    if (p >= chars.length) setDone(true);
  };

  return (
    <View style={s.container}>
      <Stack.Screen options={{ title }} />
      <View style={s.bar}>
        <TouchableOpacity style={[s.btn, s.btnSecondary]} onPress={reset}>
          <Text style={[s.btnText, { color: colors.fg }]}>Reset</Text>
        </TouchableOpacity>
        <TouchableOpacity style={s.btn} onPress={revealLetter}>
          <Text style={s.btnText}>Reveal letter</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.btn, s.btnSecondary]} onPress={revealWord}>
          <Text style={[s.btnText, { color: colors.fg }]}>Reveal word</Text>
        </TouchableOpacity>
        <Text style={s.stats}>
          {doneLetters}/{totalLetters} · {mistakes} miss
        </Text>
      </View>

      <View style={s.progress}>
        <View
          style={[
            s.progressFill,
            { width: `${totalLetters ? (doneLetters / totalLetters) * 100 : 0}%` },
          ]}
        />
      </View>

      <ScrollView ref={scrollRef} contentContainerStyle={s.scroll}>
        <TouchableOpacity activeOpacity={1} onPress={() => inputRef.current?.focus()}>
          <Text style={s.text}>
            {chars.map((ch, i) => {
              if (ch === '\n') return <Text key={i}>{'\n'}</Text>;
              if (!isLetter(ch)) return <Text key={i} style={s.punct}>{ch}</Text>;
              if (typedFlag[i]) return <Text key={i} style={s.typed}>{ch}</Text>;
              if (i === pos) return <Text key={i} style={s.cursor}>_</Text>;
              return <Text key={i} style={s.pending}>_</Text>;
            })}
          </Text>
        </TouchableOpacity>
        {done && <Text style={s.done}>Done. Tap Reset to try again.</Text>}
      </ScrollView>

      {/* Hidden input captures keystrokes from the device keyboard. */}
      <TextInput
        ref={inputRef}
        autoFocus
        autoCapitalize="none"
        autoCorrect={false}
        spellCheck={false}
        value=""
        onChangeText={onInputChange}
        style={s.hiddenInput}
        keyboardType="default"
        // hint to keyboards in iOS to keep the predictive bar off
        textContentType="none"
      />
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 8,
    gap: 6,
    borderBottomColor: colors.border,
    borderBottomWidth: 1,
    flexWrap: 'wrap',
  },
  btn: {
    backgroundColor: colors.accent,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 4,
  },
  btnSecondary: {
    backgroundColor: colors.secondaryBg,
    borderColor: colors.border,
    borderWidth: 1,
  },
  btnText: { color: '#000', fontWeight: '600', fontSize: 12 },
  stats: { color: colors.muted, fontSize: 12, marginLeft: 'auto' },
  progress: { height: 3, backgroundColor: colors.cardInnerBg },
  progressFill: { height: '100%', backgroundColor: colors.revealed },
  scroll: { padding: 16, paddingBottom: 80 },
  text: {
    color: colors.fg,
    fontFamily: 'monospace',
    fontSize: 17,
    lineHeight: 30,
  },
  punct: { color: colors.fg },
  pending: { color: '#444' },
  cursor: { backgroundColor: colors.accent, color: '#000' },
  typed: { color: colors.revealed },
  done: { color: colors.revealed, fontWeight: '700', marginTop: 16 },
  hiddenInput: {
    position: 'absolute',
    top: -1000,
    width: 1,
    height: 1,
    opacity: 0,
  },
});
