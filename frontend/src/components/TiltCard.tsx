import { useRef } from 'react'
import type { ReactNode } from 'react'

/**
 * A glass card that tilts toward the pointer in 3D and carries a moving light spot. Pure CSS transforms, so it is
 * cheap; it turns itself off for touch screens and for people who prefer reduced motion.
 */
export default function TiltCard({ children, className = '', max = 5 }: { children: ReactNode; className?: string; max?: number }) {
  const ref = useRef<HTMLDivElement>(null)

  function move(e: React.PointerEvent<HTMLDivElement>) {
    const el = ref.current
    if (!el || e.pointerType !== 'mouse' || document.documentElement.dataset.motion === 'calm') return
    const r = el.getBoundingClientRect()
    const px = (e.clientX - r.left) / r.width
    const py = (e.clientY - r.top) / r.height
    el.style.transform = `perspective(900px) rotateX(${((0.5 - py) * max).toFixed(2)}deg) rotateY(${((px - 0.5) * max * 1.4).toFixed(2)}deg) translateZ(0)`
    el.style.setProperty('--lx', `${(px * 100).toFixed(1)}%`)
    el.style.setProperty('--ly', `${(py * 100).toFixed(1)}%`)
  }

  function leave() {
    const el = ref.current
    if (el) el.style.transform = ''
  }

  return (
    <div
      ref={ref}
      onPointerMove={move}
      onPointerLeave={leave}
      className={`glass group relative rounded-2xl p-5 transition-[transform,border-color] duration-300 ease-out will-change-transform hover:border-white/25 ${className}`}
    >
      <span
        aria-hidden
        className="pointer-events-none absolute inset-0 rounded-2xl opacity-0 transition-opacity duration-300 group-hover:opacity-100"
        style={{ background: 'radial-gradient(360px circle at var(--lx,50%) var(--ly,0%), rgb(228 189 114 / 0.14), transparent 60%)' }}
      />
      <div className="relative">{children}</div>
    </div>
  )
}
