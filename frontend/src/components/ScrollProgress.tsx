import { useEffect, useRef } from 'react'

/** A hairline gradient bar across the top of the screen showing how far down the page you are. */
export default function ScrollProgress() {
  const bar = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let raf = 0
    const update = () => {
      raf = 0
      const max = document.documentElement.scrollHeight - window.innerHeight
      const p = max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0
      if (bar.current) bar.current.style.transform = `scaleX(${p})`
    }
    const onScroll = () => {
      if (!raf) raf = requestAnimationFrame(update)
    }
    update()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      if (raf) cancelAnimationFrame(raf)
    }
  }, [])

  return (
    <div aria-hidden className="pointer-events-none fixed inset-x-0 top-0 z-[65] h-[2px]">
      <div
        ref={bar}
        className="h-full origin-left scale-x-0 bg-gradient-to-r from-indigo-500 via-violet-400 to-cyan-300 shadow-[0_0_12px_1px_rgba(56,189,248,0.7)]"
      />
    </div>
  )
}
