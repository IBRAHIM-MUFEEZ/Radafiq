import React from 'react';
import { motion } from 'framer-motion';
import RadafiqLogo from './RadafiqLogo';
import GradientText from './animations/GradientText';
import BlurText from './animations/BlurText';

interface AnimatedSplashScreenProps {
  message?: string;
}

export default function AnimatedSplashScreen({ message = 'Loading your finances...' }: AnimatedSplashScreenProps) {
  return (
    <div className="radafiq-bg" style={{
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      height: '100vh', flexDirection: 'column', gap: 24,
    }}>
      {/* Animated logo — subtle float + pulse */}
      <motion.div
        animate={{ y: [0, -8, 0], scale: [1, 1.03, 1] }}
        transition={{ duration: 3, repeat: Infinity, ease: 'easeInOut' }}
      >
        <RadafiqLogo size={80} />
      </motion.div>

      {/* App name with animated gradient */}
      <GradientText colors={['#5B7FFF', '#2DD4A0', '#A78BFA']} duration={4}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 800, letterSpacing: '-0.02em' }}>
          Radafiq
        </h1>
      </GradientText>

      {/* Loading text animates in character by character */}
      <div style={{ height: 24, overflow: 'hidden' }}>
        <BlurText
          text={message}
          as="p"
          delay={0.1}
          duration={0.6}
          blurAmount={8}
          stagger={0.03}
          style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}
        />
      </div>

      {/* Subtle secondary spinner */}
      <div className="spinner" style={{ width: 24, height: 24, borderWidth: 2, opacity: 0.5 }} />
    </div>
  );
}
