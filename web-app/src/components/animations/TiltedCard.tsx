import React, { useRef, useCallback, useState } from 'react';

interface TiltedCardProps {
  children: React.ReactNode;
  className?: string;
  maxTilt?: number;
  glare?: boolean;
  glareColor?: string;
  scale?: number;
  perspective?: number;
  style?: React.CSSProperties;
}

export default function TiltedCard({
  children,
  className = '',
  maxTilt = 6,
  glare = false,
  glareColor = 'rgba(255,255,255,0.15)',
  scale = 1.01,
  perspective = 1000,
  style,
}: TiltedCardProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const [glarePos, setGlarePos] = useState({ x: 50, y: 50 });
  const [isHovered, setIsHovered] = useState(false);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (!ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    const px = (e.clientX - rect.left) / rect.width;
    const py = (e.clientY - rect.top) / rect.height;
    setTilt({ x: (px - 0.5) * maxTilt, y: (0.5 - py) * maxTilt });
    setGlarePos({ x: px * 100, y: py * 100 });
  }, [maxTilt]);

  const handleMouseLeave = useCallback(() => {
    setTilt({ x: 0, y: 0 });
    setIsHovered(false);
  }, []);

  const handleMouseEnter = useCallback(() => {
    setIsHovered(true);
  }, []);

  return (
    <div
      ref={ref}
      className={className}
      onMouseMove={handleMouseMove}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      style={{
        perspective: `${perspective}px`,
        transformStyle: 'preserve-3d',
        ...style,
      }}
    >
      <div
        style={{
          transform: isHovered
            ? `rotateX(${tilt.y}deg) rotateY(${tilt.x}deg) scale3d(${scale}, ${scale}, ${scale})`
            : 'rotateX(0) rotateY(0) scale3d(1, 1, 1)',
          transition: isHovered
            ? 'transform 0.1s ease'
            : 'transform 0.35s ease',
          position: 'relative',
          overflow: 'hidden',
          borderRadius: 'inherit',
        }}
      >
        {glare && isHovered && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              background: `radial-gradient(circle at ${glarePos.x}% ${glarePos.y}%, ${glareColor}, transparent 60%)`,
              pointerEvents: 'none',
              zIndex: 1,
              mixBlendMode: 'overlay',
            }}
          />
        )}
        <div style={{ position: 'relative', zIndex: 2 }}>
          {children}
        </div>
      </div>
    </div>
  );
}
