import React, { useRef, useEffect, useState } from 'react';

interface SplitTextProps {
  text: string;
  as?: 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6' | 'p' | 'span';
  className?: string;
  delay?: number;
  duration?: number;
  animation?: 'fade' | 'slide' | 'scale' | 'blur';
  stagger?: number;
  by?: 'chars' | 'words';
  style?: React.CSSProperties;
}

const styleId = 'split-text-anim';

function injectStyles() {
  if (document.getElementById(styleId)) return;
  const css = document.createElement('style');
  css.id = styleId;
  css.textContent = `
    @keyframes split-fade-in {
      0% { opacity: 0; }
      100% { opacity: 1; }
    }
    @keyframes split-slide-in {
      0% { opacity: 0; transform: translateY(1.2em); }
      100% { opacity: 1; transform: translateY(0); }
    }
    @keyframes split-scale-in {
      0% { opacity: 0; transform: scale(0.6); }
      100% { opacity: 1; transform: scale(1); }
    }
    @keyframes split-blur-in {
      0% { opacity: 0; filter: blur(12px); }
      100% { opacity: 1; filter: blur(0); }
    }
    .split-text-char {
      display: inline-block;
      white-space: pre;
    }
    .split-text-word {
      display: inline-block;
      white-space: nowrap;
      margin-right: 0.25em;
    }
  `;
  document.head.appendChild(css);
}

export default function SplitText({
  text,
  as: Tag = 'h1',
  className = '',
  delay = 0,
  duration = 0.5,
  animation = 'fade',
  stagger = 0.04,
  by = 'chars',
  style,
}: SplitTextProps) {
  const [mounted, setMounted] = useState(false);
  const ref = useRef<HTMLElement>(null);

  useEffect(() => {
    injectStyles();
    setMounted(true);
  }, []);

  const animName =
    animation === 'slide' ? 'split-slide-in' :
    animation === 'scale' ? 'split-scale-in' :
    animation === 'blur' ? 'split-blur-in' :
    'split-fade-in';

  const items = by === 'words' ? text.split(/(\s+)/) : [...text];

  if (!mounted) {
    return <Tag className={className} style={style}>{text}</Tag>;
  }

  return (
    <Tag
      ref={ref as React.RefObject<HTMLHeadingElement>}
      className={className}
      style={{ display: 'inline', ...style }}
      aria-label={text}
    >
      {items.map((item, i) => (
        <span
          key={`${item}-${i}`}
          className={`split-text-${by === 'words' ? 'word' : 'char'}`}
          style={{
            animation: `${animName} ${duration}s ease-out ${delay + i * stagger}s both`,
          }}
        >
          {item}
        </span>
      ))}
    </Tag>
  );
}
