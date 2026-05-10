import React, { useCallback, useRef, useState } from 'react';
import { getInitials } from '../utils/format';

interface AnimatedAvatarProps {
  name: string;
  photoUrl?: string | null;
  size?: number;
  onClick?: () => void;
  accent?: string;
  style?: React.CSSProperties;
}

export default function AnimatedAvatar({ name, photoUrl, size = 40, onClick, accent, style }: AnimatedAvatarProps) {
  const [burst, setBurst] = useState<{ x: number; y: number; id: number } | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const idRef = useRef(0);

  const handleClick = useCallback((e: React.MouseEvent) => {
    if (!onClick) return;
    const rect = ref.current?.getBoundingClientRect();
    if (rect) {
      idRef.current++;
      setBurst({ x: e.clientX - rect.left, y: e.clientY - rect.top, id: idRef.current });
      setTimeout(() => setBurst(null), 800);
    }
    onClick();
  }, [onClick]);

  return (
    <div
      ref={ref}
      className="avatar-interactive"
      onClick={handleClick}
      title={name}
      style={{
        width: size,
        height: size,
        minWidth: size,
        borderRadius: '50%',
        background: photoUrl ? 'transparent' : accent ? `${accent}22` : 'color-mix(in srgb, var(--primary) 15%, transparent)',
        border: photoUrl ? 'none' : `1px solid ${accent ? accent + '55' : 'rgba(6,182,212,0.35)'}`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontWeight: 700,
        fontSize: size * 0.4,
        color: accent || 'var(--primary)',
        flexShrink: 0,
        overflow: 'hidden',
        position: 'relative',
        ...style,
      }}
    >
      {photoUrl ? (
        <img
          src={photoUrl}
          alt=""
          referrerPolicy="no-referrer"
          style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }}
        />
      ) : (
        getInitials(name)
      )}

      {/* Click burst particles */}
      {burst && (
        <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
          {Array.from({ length: 6 }).map((_, i) => {
            const angle = (i / 6) * 360;
            const dist = 20 + Math.random() * 15;
            return (
              <div
                key={i}
                style={{
                  position: 'absolute',
                  left: burst.x,
                  top: burst.y,
                  width: 4 + Math.random() * 4,
                  height: 4 + Math.random() * 4,
                  borderRadius: '50%',
                  background: [accent || 'var(--primary)', 'var(--cyan)', 'var(--emerald)', 'var(--warning)'][i % 4],
                  animation: `confettiFall 0.6s ease-out forwards`,
                  transform: `rotate(${angle}deg) translateX(${dist}px)`,
                  opacity: 1,
                }}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}
