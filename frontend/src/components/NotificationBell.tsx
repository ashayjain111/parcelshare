import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, timeAgo } from '../lib'
import type { Inbox } from '../lib'

/** The bell: polls every 15 s, shows an unread count, and opens a panel of recent milestones. */
export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  const nav = useNavigate()
  const qc = useQueryClient()
  const inbox = useQuery({ queryKey: ['notifications'], queryFn: () => api<Inbox>('/notifications'), refetchInterval: 15000 })
  const readAll = useMutation({
    mutationFn: () => api('/notifications/read-all', { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })

  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false)
    }
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', esc)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', esc)
    }
  }, [open])

  const unread = inbox.data?.unread ?? 0
  return (
    <div ref={box} className="relative">
      <button
        aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`}
        onClick={() => {
          setOpen(!open)
          if (!open && unread > 0) readAll.mutate()
        }}
        className="relative grid h-9 w-9 cursor-pointer place-items-center rounded-xl border border-slate-900/10 bg-slate-900/[0.04] text-slate-700 transition hover:bg-slate-900/10"
      >
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M6 8a6 6 0 1 1 12 0c0 7 3 8 3 8H3s3-1 3-8" />
          <path d="M10.3 21a1.9 1.9 0 0 0 3.4 0" />
        </svg>
        {unread > 0 && (
          <span className="absolute -right-1 -top-1 grid h-4 min-w-4 place-items-center rounded-full bg-gradient-to-r from-indigo-500 to-cyan-400 px-1 text-[10px] font-bold text-white shadow-[0_0_12px_2px_rgba(56,189,248,0.6)]">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>
      <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0, y: -8, scale: 0.97 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: -8, scale: 0.97 }}
          transition={{ type: 'spring', stiffness: 460, damping: 34 }}
          style={{ transformOrigin: 'top right' }}
          className="glass absolute right-0 top-12 z-50 w-[min(92vw,22rem)] overflow-hidden rounded-2xl bg-white/95 p-1.5"
        >
          <p className="px-3 pb-1 pt-2 text-xs font-semibold uppercase tracking-wider text-slate-500">Notifications</p>
          {!inbox.data?.items.length ? (
            <p className="px-3 py-8 text-center text-sm text-slate-500">Nothing yet. Booking updates will show up here.</p>
          ) : (
            <ul className="max-h-96 overflow-y-auto">
              {inbox.data.items.map((n) => (
                <li key={n.id}>
                  <button
                    onClick={() => {
                      setOpen(false)
                      if (n.link) nav(n.link)
                    }}
                    className="block w-full cursor-pointer rounded-xl px-3 py-2.5 text-left transition hover:bg-slate-900/[0.07]"
                  >
                    <span className="flex items-start gap-2">
                      {!n.read && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-cyan-400 shadow-[0_0_8px_2px_rgba(56,189,248,0.7)]" />}
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium text-slate-900">{n.title}</span>
                        {n.body && <span className="mt-0.5 line-clamp-2 block text-xs text-slate-500">{n.body}</span>}
                        <span className="mt-1 block text-[11px] text-slate-400">{timeAgo(n.createdAt)}</span>
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </motion.div>
      )}
      </AnimatePresence>
    </div>
  )
}
