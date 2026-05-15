import React from 'react';
import { useCountUp } from '../hooks/useCountUp';

interface Props {
  value: number;
  duration?: number;
  style?: React.CSSProperties;
  className?: string;
}

/**
 * Renders a currency value with a count-up animation whenever `value` changes.
 * Drop-in replacement for {formatMoney(x)} in summary/hero/metric positions.
 */
export default function AnimatedMoney({ value, duration = 700, style, className }: Props) {
  const text = useCountUp(value, duration);
  return (
    <span style={style} className={className}>
      {text}
    </span>
  );
}
