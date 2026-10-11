import { useEffect, useState } from 'react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { EVENT_LABELS, dateTime } from '../lib'
import type { EventView, UserSummary } from '../lib'

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`glass rounded-2xl p-5 ${className}`}>{children}</div>
}

export function PageTitle({ title, sub, right }: { title: string; sub?: string; right?: ReactNode }) {
  return (
    <div className="mb-7 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight text-slate-900 sm:text-4xl">{title}</h1>
        {sub && <p className="mt-2 max-w-2xl text-sm text-slate-500">{sub}</p>}
      </div>
      {right}
    </div>
  )
}

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'
const VARIANTS: Record<Variant, string> = {
  primary:
    'btn-shine bg-gradient-to-r from-indigo-500 via-violet-500 to-cyan-400 bg-[length:200%_auto] font-semibold text-[#1b1408] shadow-[inset_0_1px_0_rgba(255,255,255,0.45),0_10px_24px_-10px_rgba(197,143,56,0.8)] hover:bg-right hover:shadow-[inset_0_1px_0_rgba(255,255,255,0.55),0_14px_30px_-10px_rgba(197,143,56,0.95)] disabled:opacity-40 disabled:shadow-none',
  secondary:
    'border border-white/12 bg-white/[0.05] text-slate-800 hover:border-white/25 hover:bg-white/[0.1] disabled:text-slate-400',
  danger:
    'bg-gradient-to-r from-red-500 to-rose-500 text-white shadow-[0_8px_30px_-10px_rgba(244,63,94,0.9)] hover:brightness-110 disabled:opacity-40 disabled:shadow-none',
  ghost: 'text-indigo-600 hover:bg-white/[0.06]',
}

export function Button({
  variant = 'primary',
  className = '',
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      {...rest}
      className={`inline-flex cursor-pointer items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition-all duration-300 active:scale-[0.98] disabled:cursor-not-allowed ${VARIANTS[variant]} ${className}`}
    />
  )
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-slate-500">{label}</span>
      {children}
      {hint && <span className="mt-1.5 block text-xs text-slate-500">{hint}</span>}
    </label>
  )
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null
  const msg = error instanceof Error ? error.message : String(error)
  return (
    <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
      {msg}
    </div>
  )
}

export function Loading() {
  // Free hosting puts the server to sleep when idle; the first request can take up to a minute. Say so instead of looking frozen.
  const [slow, setSlow] = useState(false)
  useEffect(() => {
    const t = setTimeout(() => setSlow(true), 5000)
    return () => clearTimeout(t)
  }, [])
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-14 text-sm text-slate-500" role="status">
      <div className="flex items-center gap-3">
        <span className="h-5 w-5 animate-spin rounded-full border-2 border-white/10 border-t-indigo-500" />
        Loading…
      </div>
      {slow && <p className="max-w-xs text-center text-xs text-slate-400">Still working. The server may be waking up after a quiet spell, which can take up to a minute.</p>}
    </div>
  )
}

/** A shimmering placeholder block shown while data loads, so the page keeps its shape instead of jumping. */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div aria-hidden className={`skeleton rounded-xl ${className}`} />
}

export function ListSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-3" role="status" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="glass rounded-2xl p-5">
          <Skeleton className="h-5 w-1/3" />
          <Skeleton className="mt-3 h-3.5 w-2/3" />
          <Skeleton className="mt-2 h-3.5 w-1/2" />
        </div>
      ))}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-2xl border border-dashed border-white/15 bg-white/[0.02] px-6 py-12 text-center text-sm text-slate-500">
      {children}
    </p>
  )
}

const STATUS_COLORS: Record<string, string> = {
  REQUESTED: 'bg-amber-100 text-amber-800 ring-amber-200',
  ACCEPTED: 'bg-sky-100 text-sky-800 ring-sky-100',
  HANDED_OVER: 'bg-indigo-100 text-indigo-800 ring-indigo-200',
  IN_TRANSIT: 'bg-indigo-100 text-indigo-800 ring-indigo-200',
  DELIVERED: 'bg-emerald-100 text-emerald-800 ring-emerald-200',
  RESOLVED: 'bg-emerald-100 text-emerald-800 ring-emerald-200',
  RELEASED: 'bg-emerald-100 text-emerald-800 ring-emerald-200',
  OPEN: 'bg-emerald-100 text-emerald-800 ring-emerald-200',
  DISPUTED: 'bg-red-100 text-red-800 ring-red-200',
  ON_DISPUTE_HOLD: 'bg-red-100 text-red-800 ring-red-200',
  DELIVERY_FAILED: 'bg-orange-100 text-orange-800 ring-orange-100',
  PENDING_REVIEW: 'bg-amber-100 text-amber-800 ring-amber-200',
  HELD: 'bg-amber-100 text-amber-800 ring-amber-200',
}

export function StatusBadge({ status }: { status: string }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wide ring-1 ring-inset ${STATUS_COLORS[status] ?? 'bg-slate-100 text-slate-700 ring-slate-200'}`}
    >
      {status.replaceAll('_', ' ')}
    </span>
  )
}

export function Person({ u, role }: { u: UserSummary; role?: string }) {
  return (
    <span className="text-sm">
      <span className="font-medium text-slate-900">{u.name}</span>
      {role && <span className="text-slate-500"> ({role})</span>}
      <span className="ml-2 text-xs text-slate-500">
        ★ {u.ratingAvg.toFixed(1)} · {u.reliabilityPercent}% reliable
        {u.verifiedTraveller && <span className="ml-1 text-emerald-600">✓ verified</span>}
      </span>
    </span>
  )
}

export function Timeline({ events }: { events: EventView[] }) {
  return (
    <ol className="relative ml-2 border-l border-white/10">
      {events.map((e, i) => (
        <li key={e.id} className="mb-5 ml-5 last:mb-0">
          <span
            className={`absolute -left-[7px] mt-1.5 h-3 w-3 rounded-full border-2 border-[#14110d] ${i === events.length - 1 ? 'bg-cyan-400 shadow-[0_0_14px_3px_rgba(241,212,154,0.7)]' : 'bg-indigo-500 shadow-[0_0_10px_1px_rgba(197,143,56,0.6)]'}`}
          />
          <p className="text-sm font-medium text-slate-900">{EVENT_LABELS[e.type] ?? e.type}</p>
          <p className="mt-0.5 break-words text-xs text-slate-500">
            {dateTime(e.createdAt)} · {e.actorName}
            {e.payload ? ` · ${e.payload.replace(/sha256=([0-9a-f]{10})[0-9a-f]+/g, 'sha256=$1…')}` : ''}
          </p>
        </li>
      ))}
    </ol>
  )
}
