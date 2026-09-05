// Inhibition (blue-noise) sampling: picks repel each other via Gaussian falloff.
// Same algorithm the web version uses; ensures blanks aren't clustered.
export function inhibitionSample(
  n: number,
  target: number,
  sigma: number,
  strength = 0.9,
): number[] {
  if (target >= n) return Array.from({ length: n }, (_, i) => i);
  if (target <= 0) return [];

  const weights = new Array(n).fill(1.0);
  const picked: number[] = [];

  for (let k = 0; k < target; k++) {
    let total = 0;
    for (let i = 0; i < n; i++) total += weights[i];
    if (total <= 1e-9) {
      for (let i = 0; i < n; i++) {
        if (!picked.includes(i)) {
          picked.push(i);
          break;
        }
      }
      continue;
    }
    let r = Math.random() * total;
    let idx = n - 1;
    for (let i = 0; i < n; i++) {
      r -= weights[i];
      if (r <= 0) {
        idx = i;
        break;
      }
    }
    picked.push(idx);
    const twoSigSq = 2 * sigma * sigma;
    for (let j = 0; j < n; j++) {
      const d = j - idx;
      const falloff = strength * Math.exp(-(d * d) / twoSigSq);
      weights[j] *= 1 - falloff;
    }
    weights[idx] = 0;
  }
  return picked;
}
