import React, { useRef, useEffect, useState, ReactNode } from 'react';

interface ScrollRevealProps {
  children: ReactNode;
  className?: string;
  direction?: 'up' | 'down' | 'left' | 'right';
  delay?: number;
  duration?: number;
  distance?: number;
  once?: boolean;
  rootMargin?: string;
  style?: React.CSSProperties;
}

const scrollStyleId = 'scroll-reveal-anim';

function injectScrollStyles() {
  if (document.getElementById(scrollStyleId)) return;
  const css = document.createElement('style');
  css.id = scrollStyleId;
  css.textContent = `
    .scroll-reveal-hidden {
      opacity: 0;
      transition: opacity 0.6s ease, transform 0.6s ease;
    }
    .scroll-reveal-visible {
      opacity: 1;
      transform: translate(0, 0) !important;
    }
  `;
  document.head.appendChild(css);
}
if (typeof document !== 'undefined') injectScrollStyles();

export default function ScrollReveal({
  children,
  className = '',
  direction = 'up',
  delay = 0,
  duration = 0.6,
  distance = 40,
  once = true,
  rootMargin = '-60px',
  style,
}: ScrollRevealProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true);
          if (once) observer.unobserve(el);
        } else if (!once) {
          setVisible(false);
        }
      },
      { rootMargin }
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, [once, rootMargin]);

  const directionTransform = {
    up: `translateY(${distance}px)`,
    down: `translateY(${-distance}px)`,
    left: `translateX(${distance}px)`,
    right: `translateX(${-distance}px)`,
  };

  return (
    <div
      ref={ref}
      className={`${className} ${visible ? 'scroll-reveal-visible' : 'scroll-reveal-hidden'}`}
      style={{
        transform: visible ? 'translate(0, 0)' : directionTransform[direction],
        transitionDuration: `${duration}s`,
        transitionDelay: `${delay}s`,
        ...style,
      }}
    >
      {children}
    </div>
  );
}
