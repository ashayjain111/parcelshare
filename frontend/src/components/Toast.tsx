import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'

type Kind = 'ok' | 'err'
interface Item {
  id: number
  msg: string
  kind: Kind
}

const listeners = new Set<(t: Item) => void>()
let counter = 0

/** Show a short confirmation (or error) message from anywhere, including outside React components. */
export function toast(msg: string, kind: Kind = 'ok') {
  const item = { id: ++counter, msg, kind }
  listeners.forEach((l) => l(item))
}

/** Mounted once in the layout. Announces messages to screen readers via aria-live; messages spring in and slide out. */
export function Toaster() {
  const [items, setItems] = useState<Item[]>([])

  useEffect(() => {
    const onToast = (t: Item) => {
      setItems((prev) => [...prev.slice(-2), t])
      setTimeout(() => setItems((prev) => prev.filter((x) => x.id !== t.id)), 4000)
    }
    listeners.add(onToast)
    return () => {
      listeners.delete(onToast)
    }
  }, [])

  return (
    <div aria-live="polite" role="status" className="pointer-events-none fixed right-4 top-24 z-[70] flex w-[min(92vw,22rem)] flex-col gap-2">
      <AnimatePresence initial={false}>
        {items.map((t) => (
          <motion.div
            key={t.id}
            layout
            initial={{ opacity: 0, x: 48, scale: 0.94 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: 48, scale: 0.94 }}
            transition={{ type: 'spring', stiffness: 420, damping: 32 }}
            className={`glass pointer-events-auto flex items-start gap-3 rounded-2xl px-4 py-3 text-sm ${t.kind === 'ok' ? 'text-slate-900' : 'text-red-700'}`}
          >
            <span className={`mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full text-[11px] font-bold text-white ${t.kind === 'ok' ? 'bg-gradient-to-br from-emerald-400 to-cyan-400 shadow-[0_0_14px_2px_rgba(52,211,153,0.5)]' : 'bg-red-500'}`}>
              {t.kind === 'ok' ? '✓' : '!'}
            </span>
            <span>{t.msg}</span>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  )
}
