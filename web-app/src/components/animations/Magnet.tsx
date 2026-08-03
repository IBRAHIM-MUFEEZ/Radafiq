import React, { useRef, useCallback, useState } from 'react';

interface MagnetProps {
  children: React.ReactNode;
  className?: string;
  strength?: number;
  radius?: number;
  style?: React.CSSProperties;
}

export default function Magnet({
  children,
  className = '',
  strength = 15,
  radius = 200,
  style,
}: MagnetProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState({ x: 0, y: 0 });
  const [hovering, setHovering] = useState(false);
  const raf = useRef<number>(0);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (!ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    const dx = e.clientX - cx;
    const dy = e.clientY - cy;
    const dist = Math.sqrt(dx * dx + dy * dy);

    if (dist < radius) {
      const power = (radius - dist) / radius;
      setPos({ x: dx * power, y: dy * power });
      setHovering(true);
    } else {
      setHovering(false);
    }
  }, [radius]);

  const handleMouseLeave = useCallback(() => {
    setHovering(false);
    setPos({ x: 0, y: 0 });
  }, []);

  return (
    <div
      ref={ref}
      className={className}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      style={{
        display: 'inline-block',
        transform: hovering
          ? `translate(${pos.x * (strength / 100)}px, ${pos.y * (strength / 100)}px)`
          : 'translate(0, 0)',
        transition: hovering
          ? 'transform 0.08s ease-out'
          : 'transform 0.3s ease-out',
        willChange: 'transform',
        ...style,
      }}
    >
      {children}
    </div>
  );
}
