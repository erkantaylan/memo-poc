import { useState, useEffect, useCallback } from 'react';
import { loadPrefs, savePrefs } from '@/data/storage';

const MIN = 0.8;
const MAX = 1.8;
const STEP = 0.15;

export function useFontScale() {
  const [scale, setScale] = useState(1.0);

  useEffect(() => {
    loadPrefs().then((p) => {
      if (p.fontScale) setScale(p.fontScale);
    });
  }, []);

  const increase = useCallback(() => {
    setScale((prev) => {
      const next = Math.min(MAX, parseFloat((prev + STEP).toFixed(2)));
      savePrefs({ fontScale: next });
      return next;
    });
  }, []);

  const decrease = useCallback(() => {
    setScale((prev) => {
      const next = Math.max(MIN, parseFloat((prev - STEP).toFixed(2)));
      savePrefs({ fontScale: next });
      return next;
    });
  }, []);

  return { scale, increase, decrease };
}
