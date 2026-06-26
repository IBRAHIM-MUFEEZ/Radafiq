import { animate, stagger, type Easing } from 'framer-motion';

export function fadeInUp(el: HTMLElement | string, delay = 0, duration = 500) {
  return animate(el, { opacity: [0, 1], y: [24, 0] }, { duration: duration / 1000, delay: delay / 1000, ease: 'easeOut' });
}

export function fadeIn(el: HTMLElement | string, delay = 0, duration = 400) {
  return animate(el, { opacity: [0, 1] }, { duration: duration / 1000, delay: delay / 1000, ease: 'easeOut' });
}

export function fadeInScale(el: HTMLElement | string, delay = 0, duration = 500) {
  return animate(el, { opacity: [0, 1], scale: [0.95, 1] }, { duration: duration / 1000, delay: delay / 1000, ease: 'easeOut' });
}

export function staggerFadeInUp(
  targets: string,
  staggerDelay = 60,
  _from: number | 'first' | 'center' | 'last' | 'random' = 0,
  duration = 450
) {
  return animate(targets, { opacity: [0, 1], y: [20, 0] }, {
    duration: duration / 1000,
    delay: stagger(staggerDelay / 1000),
    ease: 'easeOut' as Easing,
  });
}

export function staggerFadeIn(
  targets: string,
  staggerDelay = 50,
  _from: number | 'first' | 'center' | 'last' | 'random' = 0
) {
  return animate(targets, { opacity: [0, 1] }, {
    duration: 0.35,
    delay: stagger(staggerDelay / 1000),
    ease: 'easeOut' as Easing,
  });
}

export function slideInLeft(el: HTMLElement | string, delay = 0, duration = 500) {
  return animate(el, { opacity: [0, 1], x: [-30, 0] }, { duration: duration / 1000, delay: delay / 1000, ease: 'easeOut' });
}

export function slideInRight(el: HTMLElement | string, delay = 0, duration = 500) {
  return animate(el, { opacity: [0, 1], x: [30, 0] }, { duration: duration / 1000, delay: delay / 1000, ease: 'easeOut' });
}

export function navItemEntrance(targets: string, staggerDelay = 80) {
  return animate(targets, { opacity: [0, 1], x: [-20, 0] }, {
    duration: 0.4,
    delay: stagger(staggerDelay / 1000),
    ease: 'easeOut' as Easing,
  });
}

export function pageEntrance(el: HTMLElement | string) {
  return animate(el, { opacity: [0, 1], y: [16, 0] }, { duration: 0.4, ease: 'easeOut' });
}

export function cardEntrance(el: HTMLElement | string, index = 0, staggerMs = 60) {
  return animate(el, { opacity: [0, 1], y: [16, 0] }, { duration: 0.4, delay: (index * staggerMs) / 1000, ease: 'easeOut' });
}

export function countUp(
  obj: { value: number },
  from: number,
  to: number,
  duration = 800,
  ease: string = 'easeOut'
) {
  return animate(obj, { value: [from, to] }, { duration: duration / 1000, ease: ease as Easing });
}

export function pulse(el: HTMLElement | string, scale = 1.05, duration = 300) {
  return animate(el, { scale: [1, scale, 1] }, { duration: duration / 1000, ease: 'easeOut' });
}

export function shake(el: HTMLElement | string) {
  return animate(el, { x: [0, -6, 6, -4, 4, -2, 2, 0] }, { duration: 0.5, ease: 'easeOut' });
}
