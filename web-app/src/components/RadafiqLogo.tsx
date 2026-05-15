import React from 'react';

interface RadafiqLogoProps {
  size?: number;
  className?: string;
}

export default function RadafiqLogo({ size = 48, className }: RadafiqLogoProps) {
  return (
    <img
      src="/logo-Photoroom.png"
      alt="Radafiq logo"
      width={size}
      height={size}
      className={className}
      style={{
        display: 'block',
        objectFit: 'contain',
      }}
    />
  );
}
