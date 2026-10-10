import { useRef } from 'react'
import type { ReactNode } from 'react'

/** Wraps a button/link so it leans toward the pointer as it gets close. Off in calm mode and for touch input. */
export default function Magnetic({ children, strength = 0.28 }: { children: ReactNode; strength?: number }) {
  const ref = useRef<HTMLDivElement>(null)

  function move(e: React.PointerEvent<HTMLDivElement>) {
    const el = ref.current
    if (!el || e.pointerType !== 'mouse' || document.documentElement.dataset.motion === 'calm') return
    const r = el.getBoundingClientRect()
    const dx = e.clientX - (r.left + r.width / 2)
    const dy = e.clientY - (r.top + r.height / 2)
    el.style.transform = `translate(${(dx * strength).toFixed(1)}px, ${(dy * strength).toFixed(1)}px)`
  }

  return (
    <div
      ref={ref}
      onPointerMove={move}
      onPointerLeave={() => { if (ref.current) ref.current.style.transform = '' }}
      className="inline-block transition-transform duration-300 ease-out will-change-transform"
    >
      {children}
    </div>
  )
}
