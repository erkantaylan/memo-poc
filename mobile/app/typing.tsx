import { useEffect, useMemo, useRef, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
} from 'react-native';
import { useLocalSearchParams, Stack } from 'expo-router';
import { colors } from '@/theme/colors';
import { getTextById, loadProgress, saveProgress } from '@/data/storage';
import { canon, isLetter } from '@/data/canonicalize';
import { useFontScale } from '@/hooks/useFontScale';

// Lines to show above and below the cursor line
const LINES_BEFORE = 6;
const LINES_AFTER = 24;

export default function TypingScreen() {
  const { id, pos: paramPos } = useLocalSearchParams<{ id: string; pos?: string }>();
  const [title, setTitle] = useState('');
  const [chars, setChars] = useState<string[]>([]);
  const [pos, setPos] = useState(0);
  const [typedFlag, setTypedFlag] = useState<boolean[]>([]);
  const [doneLetters, setDoneLetters] = useState(0);
  const [mistakes, setMistakes] = useState(0);
  const [done, setDone] = useState(false);
  const [badFlash, setBadFlash] = useState(false);
  const inputRef = useRef<TextInput>(null);
  const scrollRef = useRef<ScrollView>(null);
  const { scale, increase, decrease } = useFontScale();
  const loadedRef = useRef(false);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    loadedRef.current = false;
    (async () => {
      const t = id ? await getTextById(id) : null;
      if (t) {
        setTitle(t.title);
        const ch = [...t.body];
        setChars(ch);

        // Determine starting position
        let startPos = 0;
        while (startPos < ch.length && !isLetter(ch[startPos])) startPos++;

        if (paramPos !== undefined) {
          startPos = Math.max(0, Math.min(ch.length, parseInt(paramPos, 10) || 0));
        } else {
          const prog = await loadProgress(t.id, 'typing');
          if (prog?.pos !== undefined) {
            startPos = Math.min(ch.length, prog.pos);
          }
        }

        // Build typedFlag based on starting position (chars before pos are "typed")
        const flag = new Array(ch.length).fill(false);
        let done = 0;
        for (let i = 0; i < startPos; i++) {
          if (isLetter(ch[i])) { flag[i] = true; done++; }
        }
        setTypedFlag(flag);
        setDoneLetters(done);
        setPos(startPos);
        loadedRef.current = true;
      }
    })();
  }, [id, paramPos]);

  // Debounced save of position
  useEffect(() => {
    if (!id || !loadedRef.current) return;
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      saveProgress(id, 'typing', { pos });
    }, 800);
    return () => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    };
  }, [id, pos]);

  // Precompute line start positions once — O(n) but runs only when chars change
  const lineStarts = useMemo(() => {
    const s = [0];
    for (let i = 0; i < chars.length; i++) {
      if (chars[i] === '\n') s.push(i + 1);
    }
    return s;
  }, [chars]);

  // Which line is cursor on? Binary search — O(log n)
  const curLine = useMemo(() => {
    let lo = 0, hi = lineStarts.length - 1;
    while (lo < hi) {
      const mid = (lo + hi + 1) >> 1;
      if (lineStarts[mid] <= pos) lo = mid;
      else hi = mid - 1;
    }
    return lo;
  }, [lineStarts, pos]);

  const totalLetters = useMemo(() => chars.filter(isLetter).length, [chars]);

  // Visible window: LINES_BEFORE lines above cursor + LINES_AFTER below
  const windowLineStart = Math.max(0, curLine - LINES_BEFORE);
  const windowLineEnd = Math.min(lineStarts.length - 1, curLine + LINES_AFTER);
  const charStart = lineStarts[windowLineStart];
  const charEnd =
    windowLineEnd + 1 < lineStarts.length ? lineStarts[windowLineEnd + 1] : chars.length;

  // Scroll to top when the window shifts forward
  const prevWindowStart = useRef(0);
  if (windowLineStart !== prevWindowStart.current) {
    prevWindowStart.current = windowLineStart;
    // Fire scroll asynchronously so it doesn't block the render
    setTimeout(() => scrollRef.current?.scrollTo({ y: 0, animated: false }), 0);
  }

  const reset = () => {
    setTypedFlag(new Array(chars.length).fill(false));
    setDoneLetters(0);
    let p = 0;
    while (p < chars.length && !isLetter(chars[p])) p++;
    setPos(p);
    setMistakes(0);
    setDone(false);
    inputRef.current?.focus();
  };

  const undo = () => {
    if (pos === 0) return;
    let p = pos - 1;
    while (p > 0 && !isLetter(chars[p])) p--;
    if (!isLetter(chars[p])) return;
    const next = typedFlag.slice();
    next[p] = false;
    setTypedFlag(next);
    setPos(p);
    setDoneLetters((d) => Math.max(0, d - 1));
    setDone(false);
  };

  const revealLetter = () => {
    if (pos >= chars.length || !isLetter(chars[pos])) return;
    setMistakes((m) => m + 1);
    const next = typedFlag.slice();
    next[pos] = true;
    let p = pos + 1;
    while (p < chars.length && !isLetter(chars[p])) p++;
    setTypedFlag(next);
    setPos(p);
    setDoneLetters((d) => d + 1);
    if (p >= chars.length) setDone(true);
  };

  const revealWord = () => {
    if (pos >= chars.length) return;
    setMistakes((m) => m + 1);
    const next = typedFlag.slice();
    let p = pos;
    let revealed = 0;
    while (p < chars.length && !/\s/.test(chars[p])) {
      if (isLetter(chars[p])) { next[p] = true; revealed++; }
      p++;
    }
    while (p < chars.length && !isLetter(chars[p])) p++;
    setTypedFlag(next);
    setPos(p);
    setDoneLetters((d) => d + revealed);
    if (p >= chars.length) setDone(true);
  };

  const onInputChange = (text: string) => {
    if (!text.length) return;
    const last = text[text.length - 1];
    if (last === '\n') return;
    if (!isLetter(last)) { inputRef.current?.clear(); return; }
    if (pos >= chars.length) return;
    if (canon(last) === canon(chars[pos])) {
      const next = typedFlag.slice();
      next[pos] = true;
      let p = pos + 1;
      while (p < chars.length && !isLetter(chars[p])) p++;
      setTypedFlag(next);
      setPos(p);
      setDoneLetters((d) => d + 1);
      if (p >= chars.length) setDone(true);
    } else {
      setMistakes((m) => m + 1);
      setBadFlash(true);
      setTimeout(() => setBadFlash(false), 180);
    }
    inputRef.current?.clear();
  };

  // Render only the window slice — at most ~30 lines regardless of text length
  const windowChars = chars.slice(charStart, charEnd);

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
        <TouchableOpacity style={[s.btn, s.btnSecondary]} onPress={reset}>
          <Text style={[s.btnText, { color: colors.fg }]}>Reset</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.btn, s.btnSecondary]} onPress={undo}>
          <Text style={[s.btnText, { color: colors.fg }]}>Undo</Text>
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
          <Text style={[s.text, { fontSize: 17 * scale, lineHeight: 30 * scale }]}>
            {windowChars.map((ch, wi) => {
              const i = charStart + wi;
              if (ch === '\n') return <Text key={i}>{'\n'}</Text>;
              if (!isLetter(ch)) return <Text key={i} style={s.punct}>{ch}</Text>;
              if (typedFlag[i]) return <Text key={i} style={s.typed}>{ch}</Text>;
              if (i === pos) return (
                <Text key={i} style={[s.cursor, badFlash && s.cursorBad]}>_</Text>
              );
              return <Text key={i} style={s.pending}>_</Text>;
            })}
          </Text>
        </TouchableOpacity>
        {done && <Text style={s.done}>Done! Tap Reset to try again.</Text>}
        <View style={{ height: 80 }} />
      </ScrollView>

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
        textContentType="none"
      />
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
    gap: 6,
    borderBottomColor: colors.border,
    borderBottomWidth: 1,
    flexWrap: 'wrap',
  },
  btn: { backgroundColor: colors.accent, paddingHorizontal: 10, paddingVertical: 6, borderRadius: 4 },
  btnSecondary: { backgroundColor: colors.secondaryBg, borderColor: colors.border, borderWidth: 1 },
  btnText: { color: '#000', fontWeight: '600', fontSize: 12 },
  stats: { color: colors.muted, fontSize: 12, marginLeft: 'auto' },
  progress: { height: 3, backgroundColor: colors.cardInnerBg },
  progressFill: { height: '100%', backgroundColor: colors.revealed },
  scroll: { padding: 16 },
  text: { color: colors.fg, fontFamily: 'monospace' },
  punct: { color: colors.fg },
  pending: { color: '#444' },
  cursor: { backgroundColor: colors.accent, color: '#000' },
  cursorBad: { backgroundColor: colors.badFlash, color: '#fff' },
  typed: { color: colors.revealed },
  done: { color: colors.revealed, fontWeight: '700', marginTop: 16 },
  hiddenInput: { position: 'absolute', top: -1000, width: 1, height: 1, opacity: 0 },
});
