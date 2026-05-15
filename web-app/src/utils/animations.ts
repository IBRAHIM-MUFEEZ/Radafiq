import { animate, stagger } from 'animejs';

export function fadeInUp(el: HTMLElement | string, delay = 0, duration = 500) {
  return animate(el, {
    opacity: [0, 1],
    translateY: [24, 0],
    duration,
    delay,
    ease: 'outCubic',
  });
}

export function fadeIn(el: HTMLElement | string, delay = 0, duration = 400) {
  return animate(el, {
    opacity: [0, 1],
    duration,
    delay,
    ease: 'outCubic',
  });
}

export function fadeInScale(el: HTMLElement | string, delay = 0, duration = 500) {
  return animate(el, {
    opacity: [0, 1],
    scale: [0.95, 1],
    duration,
    delay,
    ease: 'outCubic',
  });
}

export function staggerFadeInUp(
  targets: string,
  staggerDelay = 60,
  from: number | 'first' | 'center' | 'last' | 'random' = 0,
  duration = 450
) {
  return animate(targets, {
    opacity: [0, 1],
    translateY: [20, 0],
    duration,
    delay: stagger(staggerDelay, { from, ease: 'outCubic' }),
    ease: 'outCubic',
  });
}

export function staggerFadeIn(
  targets: string,
  staggerDelay = 50,
  from: number | 'first' | 'center' | 'last' | 'random' = 0
) {
  return animate(targets, {
    opacity: [0, 1],
    duration: 350,
    delay: stagger(staggerDelay, { from, ease: 'outCubic' }),
    ease: 'outCubic',
  });
}

export function slideInLeft(el: HTMLElement | string, delay = 0, duration = 500) {
  return animate(el, {
    opacity: [0, 1],
    translateX: [-30, 0],
    duration,
    delay,
    ease: 'outCubic',
  });
}

export function slideInRight(el: HTMLElement | string, delay = 0, duration = 500) {
  return animate(el, {
    opacity: [0, 1],
    translateX: [30, 0],
    duration,
    delay,
    ease: 'outCubic',
  });
}

export function countUp(
  obj: { value: number },
  from: number,
  to: number,
  duration = 800,
  ease = 'outCubic'
) {
  return animate(obj, {
    value: [from, to],
    duration,
    ease,
  });
}

export function pulse(el: HTMLElement | string, scale = 1.05, duration = 300) {
  return animate(el, {
    scale: [1, scale, 1],
    duration,
    ease: 'outCubic',
  });
}

export function shake(el: HTMLElement | string) {
  return animate(el, {
    translateX: [0, -6, 6, -4, 4, -2, 2, 0],
    duration: 500,
    ease: 'outCubic',
  });
}

export function navItemEntrance(targets: string, staggerDelay = 80) {
  return animate(targets, {
    opacity: [0, 1],
    translateX: [-20, 0],
    duration: 400,
    delay: stagger(staggerDelay, { from: 'first' as const, ease: 'outCubic' }),
    ease: 'outCubic',
  });
}

export function pageEntrance(el: HTMLElement | string) {
  return animate(el, {
    opacity: [0, 1],
    translateY: [16, 0],
    duration: 400,
    ease: 'outCubic',
  });
}

export function cardEntrance(el: HTMLElement | string, index = 0, staggerMs = 60) {
  return animate(el, {
    opacity: [0, 1],
    translateY: [16, 0],
    duration: 400,
    delay: index * staggerMs,
    ease: 'outCubic',
  });
}
