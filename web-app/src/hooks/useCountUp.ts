import { useState, useEffect, useRef } from 'react';
import { formatMoney } from '../utils/format';

/**
 * Animates a numeric value from 0 to `target` over `duration` ms using
 * an easeOutCubic curve, then formats it with formatMoney().
 *
 * Re-triggers whenever `target` changes (e.g. data loads or filter changes).
 */
export function useCountUp(target: number, duration = 700): string {
  const [display, setDisplay] = useState(() => formatMoney(target));
  const rafRef = useRef<number | null>(null);
  const startRef = useRef<number | null>(null);
  const fromRef = useRef(0);

  useEffect(() => {
    // Cancel any in-progress animation
    if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);

    // If target is 0 or very small, skip animation
    if (target <= 0) {
      setDisplay(formatMoney(target));
      return;
    }

    fromRef.current = 0;
    startRef.current = null;

    const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3);

    const step = (timestamp: number) => {
      if (startRef.current === null) startRef.current = timestamp;
      const elapsed = timestamp - startRef.current;
      const progress = Math.min(elapsed / duration, 1);
      const eased = easeOutCubic(progress);
      const current = fromRef.current + (target - fromRef.current) * eased;
      setDisplay(formatMoney(current));
      if (progress < 1) {
        rafRef.current = requestAnimationFrame(step);
      } else {
        setDisplay(formatMoney(target));
      }
    };

    rafRef.current = requestAnimationFrame(step);
    return () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
  }, [target, duration]);

  return display;
}
