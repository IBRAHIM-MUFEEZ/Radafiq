import React, { useState, useEffect } from 'react';

interface BlurTextProps {
  text: string;
  as?: 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6' | 'p' | 'span';
  className?: string;
  delay?: number;
  duration?: number;
  blurAmount?: number;
  stagger?: number;
  by?: 'chars' | 'words';
  style?: React.CSSProperties;
}

const blurStyleId = 'blur-text-anim';

function injectBlurStyles() {
  if (document.getElementById(blurStyleId)) return;
  const css = document.createElement('style');
  css.id = blurStyleId;
  css.textContent = `
    .blur-text-char, .blur-text-word {
      display: inline-block;
      white-space: pre;
    }
  `;
  document.head.appendChild(css);
}
if (typeof document !== 'undefined') injectBlurStyles();

export default function BlurText({
  text,
  as: Tag = 'h1',
  className = '',
  delay = 0,
  duration = 0.4,
  blurAmount = 16,
  stagger = 0.04,
  by = 'chars',
  style,
}: BlurTextProps) {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    injectBlurStyles();
    setMounted(true);
  }, []);

  const items = by === 'words' ? text.split(/(\s+)/) : [...text];

  if (!mounted) {
    return <Tag className={className} style={style}>{text}</Tag>;
  }

  return (
    <Tag
      className={className}
      style={{ display: 'inline', ...style }}
      aria-label={text}
    >
      {items.map((item, i) => (
        <span
          key={`${item}-${i}`}
          className={`blur-text-${by === 'words' ? 'word' : 'char'}`}
          style={{
            opacity: 0,
            filter: `blur(${blurAmount}px)`,
            animation: `blur-text-in ${duration}s ease-out ${delay + i * stagger}s forwards`,
          }}
        >
          {item}
        </span>
      ))}
    </Tag>
  );
}

/* Inject the blur animation keyframes */
(function injectBlurKeyframes() {
  if (typeof document === 'undefined') return;
  const id = 'blur-text-keyframes';
  if (document.getElementById(id)) return;
  const css = document.createElement('style');
  css.id = id;
  css.textContent = `
    @keyframes blur-text-in {
      0% { opacity: 0; filter: blur(var(--blur-amount, 16px)); }
      100% { opacity: 1; filter: blur(0); }
    }
  `;
  document.head.appendChild(css);
  // Set CSS variable on body
  document.documentElement.style.setProperty('--blur-amount', '16px');
})();
