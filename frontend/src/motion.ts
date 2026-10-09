import { useEffect, useState } from 'react'

/**
 * Motion preference. "full" = everything animates. "calm" = the scroll-driven story still plays (it only moves when
 * the visitor scrolls) but ambient spinning, pulsing, shimmer and smooth-scroll inertia are turned right down.
 * Defaults to the operating system's "reduce motion" setting, and the visitor can override it from any page.
 */
export type Motion = 'full' | 'calm'

const KEY = 'ps_motion'

export function initialMotion(): Motion {
  try {
    const v = localStorage.getItem(KEY)
    if (v === 'full' || v === 'calm') return v
  } catch {
    /* storage unavailable: fall through to the OS setting */
  }
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'calm' : 'full'
}

export function applyMotion(m: Motion) {
  document.documentElement.dataset.motion = m
}

export const currentMotion = (): Motion => (document.documentElement.dataset.motion === 'calm' ? 'calm' : 'full')

export function setMotion(m: Motion) {
  try {
    localStorage.setItem(KEY, m)
  } catch {
    /* ignore */
  }
  applyMotion(m)
  window.dispatchEvent(new Event('ps-motion'))
}

export function useMotion(): [Motion, (m: Motion) => void] {
  const [m, setM] = useState<Motion>(currentMotion)
  useEffect(() => {
    const on = () => setM(currentMotion())
    window.addEventListener('ps-motion', on)
    return () => window.removeEventListener('ps-motion', on)
  }, [])
  return [m, setMotion]
}
