import Lenis from 'lenis'
import { Suspense, lazy, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { useAuth } from '../auth'
import ErrorBoundary from '../components/ErrorBoundary'
import Magnetic from '../components/Magnetic'
import Reveal from '../components/Reveal'
import TiltCard from '../components/TiltCard'
import { useMotion } from '../motion'
import { webglAvailable } from '../scene/env'

// The 3D scene is large (three.js), so it loads after the page shell and only on this screen.
const CorridorScene = lazy(() => import('../scene/CorridorScene'))

const HUES = ['bg-blue-500/15 text-blue-700', 'bg-emerald-500/15 text-emerald-700', 'bg-purple-500/15 text-purple-700', 'bg-orange-500/15 text-orange-700', 'bg-sky-500/20 text-sky-700', 'bg-rose-500/15 text-rose-700']
const DOTS = ['bg-blue-500', 'bg-orange-400', 'bg-emerald-500', 'bg-purple-500', 'bg-sky-400', 'bg-rose-400']
const CITIES = ['Delhi NCR', 'Lucknow', 'Kanpur', 'Jaipur', 'Chandigarh', 'Dehradun', 'Agra', 'Varanasi']

/** Reveals a line of text word by word, each word blurring into focus a beat after the last. */
function Words({ text, className = '', start = 0 }: { text: string; className?: string; start?: number }) {
  return (
    <>
      {text.split(' ').map((w, i) => (
        <span key={i}>
          <span className="word-mask"><span className={`word ${className}`} style={{ animationDelay: `${(start + i) * 70 + 120}ms` }}>{w}</span></span>{' '}
        </span>
      ))}
    </>
  )
}

const shadow = '[text-shadow:0_1px_28px_rgb(255_255_255/0.95)]'

function Section({ id, stage, side = 'left', children, className = '' }: { id?: string; stage: number; side?: 'left' | 'right'; children: ReactNode; className?: string }) {
  return (
    <section id={id} data-stage={stage} className={`relative mx-auto flex min-h-[105svh] max-w-6xl items-center px-5 py-24 ${side === 'right' ? 'md:justify-end' : ''} ${className}`}>
      {children}
    </section>
  )
}

function Eyebrow({ n, children }: { n: string; children: ReactNode }) {
  return (
    <p className="mb-5 flex items-center gap-4 text-[11px] font-semibold uppercase tracking-[0.26em] text-cyan-300">
      <span className="numeral text-3xl leading-none">{n}</span>
      <span className="draw-line h-px w-14 bg-cyan-300/70" />
      {children}
    </p>
  )
}

function Heading({ children }: { children: ReactNode }) {
  return <h2 className={`font-display text-4xl font-medium leading-[1.04] tracking-[-0.022em] text-slate-900 sm:text-5xl lg:text-[3.6rem] ${shadow}`}>{children}</h2>
}

function Lead({ children }: { children: ReactNode }) {
  return <p className={`mt-5 max-w-lg text-base leading-relaxed text-slate-600 sm:text-lg ${shadow}`}>{children}</p>
}

// ---------------------------------------------------------------- mock UI cards (illustrations of real screens)

function ScoreBar({ label, value, max }: { label: string; value: number; max: number }) {
  return (
    <div className="flex items-center gap-3 text-xs">
      <span className="w-20 text-slate-500">{label}</span>
      <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-900/10">
        <span className="block h-full rounded-full bg-gradient-to-r from-indigo-500 to-cyan-400" style={{ width: `${(value / max) * 100}%` }} />
      </span>
      <span className="w-6 text-right tabular-nums text-slate-700">{value}</span>
    </div>
  )
}

function MatchCard() {
  return (
    <TiltCard className="w-full max-w-md">
      <div className="flex items-start justify-between">
        <div>
          <p className="font-display text-lg font-semibold text-slate-900">Noida → Lucknow</p>
          <p className="text-xs text-slate-500">Train · 8 Oct, 10:15 · 3 kg free</p>
        </div>
        <div className="relative grid h-14 w-14 place-items-center">
          <svg viewBox="0 0 36 36" className="absolute inset-0 -rotate-90">
            <circle cx="18" cy="18" r="15.5" fill="none" stroke="rgb(11 27 58 / .1)" strokeWidth="2.5" />
            <circle cx="18" cy="18" r="15.5" fill="none" stroke="url(#sg)" strokeWidth="2.5" strokeLinecap="round" strokeDasharray="97.4" strokeDashoffset="10.7" />
            <defs><linearGradient id="sg"><stop stopColor="#2563eb" /><stop offset="1" stopColor="#38bdf8" /></linearGradient></defs>
          </svg>
          <span className="font-display text-lg font-semibold text-slate-900">89</span>
        </div>
      </div>
      <p className="mt-3 text-sm text-slate-700">Priya S. <span className="text-xs text-slate-500">★ 4.4 · 97% reliable</span> <span className="text-xs text-emerald-600">✓ verified</span></p>
      <div className="mt-4 space-y-2">
        <ScoreBar label="Route" value={40} max={40} />
        <ScoreBar label="Timing" value={10} max={15} />
        <ScoreBar label="Rating" value={13} max={15} />
        <ScoreBar label="Reliability" value={15} max={15} />
        <ScoreBar label="Price" value={10} max={10} />
      </div>
      <div className="mt-5 flex items-center justify-between border-t border-slate-900/10 pt-4">
        <span className="font-display text-2xl font-semibold text-slate-900">₹80</span>
        <span className="rounded-lg bg-gradient-to-r from-indigo-500 to-cyan-400 px-4 py-2 text-sm font-semibold text-white">Book &amp; pay</span>
      </div>
    </TiltCard>
  )
}

function HandoverCard() {
  return (
    <TiltCard className="w-full max-w-md">
      <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Handover code · single use · 15 min</p>
      <div className="mt-3 flex gap-2">
        {'048213'.split('').map((d, i) => (
          <span key={i} className="grid h-14 flex-1 place-items-center rounded-xl border border-cyan-300/30 bg-cyan-400/10 font-display text-3xl font-semibold text-cyan-200 shadow-[0_0_24px_-6px_rgba(56,189,248,0.7)]">{d}</span>
        ))}
      </div>
      <ul className="mt-5 space-y-2.5 text-sm">
        {['Traveller opened the parcel and inspected the contents', '2 photos of the contents added', 'Code entered, handover confirmed'].map((t) => (
          <li key={t} className="flex items-center gap-3 text-slate-700">
            <span className="grid h-5 w-5 place-items-center rounded-full bg-emerald-400/20 text-xs text-emerald-300">✓</span>{t}
          </li>
        ))}
      </ul>
    </TiltCard>
  )
}

function ChainCard() {
  const rows = [
    ['Booking requested', 'a91f3c07e2…'],
    ['Traveller accepted', '5be0d412aa…'],
    ['Photo evidence added', 'c3d7710f9b…'],
    ['Handover confirmed', '0f48b2e6d1…'],
    ['Delivery confirmed', '7ac19e55f0…'],
  ]
  return (
    <TiltCard className="w-full max-w-md">
      <p className="mb-4 text-xs font-semibold uppercase tracking-wider text-slate-500">Chain of custody</p>
      <ol className="space-y-3">
        {rows.map(([t, h], i) => (
          <li key={t} className="flex items-center gap-3">
            <span className="relative grid h-7 w-7 shrink-0 place-items-center rounded-lg border border-indigo-300/40 bg-indigo-400/10 text-[10px] font-bold text-indigo-300">{i + 1}</span>
            <span className="min-w-0 flex-1 text-sm text-slate-800">{t}</span>
            <code className="rounded-md bg-slate-900/5 px-2 py-0.5 font-mono text-[11px] text-cyan-300">{h}</code>
          </li>
        ))}
      </ol>
      <p className="mt-5 flex items-center gap-2 border-t border-slate-900/10 pt-4 text-sm text-emerald-300"><span>✓</span> History intact. 5 events, hash chain verified</p>
    </TiltCard>
  )
}

function PayCard() {
  return (
    <TiltCard className="w-full max-w-md">
      <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Payment</p>
      <div className="mt-3 space-y-2.5 text-sm">
        <div className="flex justify-between text-slate-700"><span>Paid by sender</span><span className="font-display text-lg text-slate-900">₹100</span></div>
        <div className="flex justify-between text-slate-500"><span>Platform fee</span><span>− ₹10</span></div>
        <div className="flex justify-between border-t border-slate-900/10 pt-2.5 text-slate-800"><span>Traveller earns</span><span className="font-display text-lg text-emerald-300">₹90</span></div>
      </div>
      <div className="mt-5 grid grid-cols-3 gap-2 text-center text-[11px]">
        {[['Held', 'on booking'], ['48 h', 'dispute window'], ['Released', 'on delivery']].map(([a, b], i) => (
          <div key={a} className={`rounded-xl border px-2 py-2.5 ${i === 2 ? 'border-emerald-300/30 bg-emerald-400/10 text-emerald-200' : 'border-slate-900/10 bg-slate-900/[0.03] text-slate-700'}`}>
            <p className="font-display text-sm font-semibold">{a}</p><p className="mt-0.5 text-slate-500">{b}</p>
          </div>
        ))}
      </div>
    </TiltCard>
  )
}

const Icon = ({ d }: { d: string }) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d={d} /></svg>
)

const FEATURES: [string, string, string][] = [
  ['Smart matching', 'Hard route, date and capacity rules first, then a transparent 0–100 score from timing, ratings, reliability and price.', 'M3 12h4l3-8 4 16 3-8h4'],
  ['Payment held until delivered', 'The sender pays up front, the money waits. It is released after delivery, with a dispute window in between.', 'M12 3 4 6v6c0 4.5 3.4 8 8 9 4.6-1 8-4.5 8-9V6l-8-3Z'],
  ['Code and QR handover', 'A single-use code, shown as a QR, confirms the exchange. The traveller must inspect the contents first.', 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2v2h-2zM18 18h2v2h-2z'],
  ['Photo evidence', 'Photos are re-encoded on our server, so GPS and device data are stripped, and each one is fingerprinted into the history.', 'M4 8h3l2-3h6l2 3h3v11H4zM12 17a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z'],
  ['Tamper-evident history', 'Every event is hash-chained to the one before it. Edit the past and verification fails.', 'M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1'],
  ['Fair disputes', 'Either side can raise one. An admin reviews the evidence timeline and releases, refunds or splits the payment.', 'M12 3v18M5 7l-3 7a4 4 0 0 0 6 0L5 7Zm14 0-3 7a4 4 0 0 0 6 0l-3-7ZM5 7h14'],
]

// ---------------------------------------------------------------- page

function Landing() {
  const progress = useRef(0)
  const [gl] = useState(webglAvailable)
  const [mode] = useMotion()
  const calm = mode === 'calm'
  const [mobile, setMobile] = useState(() => window.innerWidth < 768)

  useEffect(() => {
    const onResize = () => setMobile(window.innerWidth < 768)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  useEffect(() => {
    const lenis = calm ? null : new Lenis({ lerp: 0.085, wheelMultiplier: 0.95 })
    let raf = 0
    const loop = (time: number) => {
      lenis?.raf(time)
      raf = requestAnimationFrame(loop)
    }
    raf = requestAnimationFrame(loop)
    // The story position (0..1) is interpolated between the real on-screen centres of the sections, so the 3D scene
    // is always showing the stage that matches the text in view, whatever the section heights are.
    const onScroll = () => {
      const stops = [...document.querySelectorAll<HTMLElement>('[data-stage]')]
        .map((el) => {
          const r = el.getBoundingClientRect()
          return { y: r.top + window.scrollY + r.height / 2, t: Number(el.dataset.stage) }
        })
        .sort((a, b) => a.y - b.y)
      if (!stops.length) return
      const probe = window.scrollY + window.innerHeight * 0.5
      if (probe <= stops[0].y) progress.current = stops[0].t
      else if (probe >= stops[stops.length - 1].y) progress.current = stops[stops.length - 1].t
      else {
        const i = stops.findIndex((s) => s.y >= probe)
        const a = stops[i - 1]
        const b = stops[i]
        progress.current = a.t + ((probe - a.y) / (b.y - a.y)) * (b.t - a.t)
      }
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('scroll', onScroll)
      lenis?.destroy()
    }
  }, [calm])

  return (
    <div className="relative">
      {/* fixed 3D layer behind the story; falls back to the CSS aurora if WebGL is unavailable */}
      {gl && (
        <div className="pointer-events-none fixed inset-0 z-0" aria-hidden>
          <Suspense fallback={null}>
            <ErrorBoundary fallback={null}>
              <CorridorScene progress={progress} mobile={mobile} calm={calm} />
            </ErrorBoundary>
          </Suspense>
        </div>
      )}

      <div className="relative z-10">
        {/* 00 hero */}
        <Section stage={0} className={`min-h-[100svh] ${mobile ? 'items-end pb-16' : 'pb-44 pt-28'}`}>
          <div
            aria-hidden
            className="pointer-events-none absolute inset-0 bg-[linear-gradient(rgb(37_99_235/0.07)_1px,transparent_1px),linear-gradient(90deg,rgb(37_99_235/0.07)_1px,transparent_1px)] bg-[size:84px_84px] [mask-image:radial-gradient(ellipse_70%_60%_at_30%_50%,black,transparent)]"
          />
          <div className="relative max-w-xl">
            <Reveal delay={90}>
              <h1 className={`font-display text-5xl font-semibold leading-[1.0] tracking-[-0.025em] text-slate-900 sm:text-6xl lg:text-[5.4rem] ${shadow}`}>
                <Words text="Send it with someone" /> <Words text="already going there." className="text-gradient" start={4} />
              </h1>
            </Reveal>
            <Reveal delay={180}>
              <p className={`mt-6 max-w-lg text-base leading-relaxed text-slate-600 sm:text-lg ${shadow}`}>
                ParcelShare matches your parcel with travellers on the same bus, train or car ride. Verified people, a one-time handover code,
                photo proof, and payment held until it is delivered.
              </p>
            </Reveal>
            <Reveal delay={270} className="mt-9 flex flex-wrap items-center gap-3">
              <Magnetic>
                <Link to="/register" className="btn-shine btn-ring inline-block rounded-2xl bg-gradient-to-r from-indigo-500 via-violet-500 to-cyan-400 px-7 py-3.5 text-sm font-semibold text-white shadow-[0_10px_40px_-8px_rgba(37,99,235,0.95)] transition hover:brightness-110 active:scale-[0.98]">
                  Get started free
                </Link>
              </Magnetic>
              <Magnetic strength={0.18}>
                <Link to="/login" className="inline-block rounded-2xl border border-slate-900/15 bg-slate-900/[0.05] px-7 py-3.5 text-sm font-semibold text-slate-900 backdrop-blur transition hover:bg-slate-900/10">
                  Log in
                </Link>
              </Magnetic>
            </Reveal>
          </div>
          <div className="pointer-events-none absolute right-5 top-[22%] hidden w-64 animate-float xl:block xl:right-[5%]">
            <div className="glass rounded-2xl p-4">
              <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-500">Best match</p>
              <div className="mt-2 flex items-center justify-between">
                <div>
                  <p className="font-display text-base font-semibold text-slate-900">Delhi → Lucknow</p>
                  <p className="text-xs text-slate-500">Priya S. · ★ 4.4 · verified</p>
                </div>
                <span className="grid h-11 w-11 place-items-center rounded-full bg-gradient-to-br from-indigo-500 to-cyan-400 font-display text-sm font-semibold">89</span>
              </div>
            </div>
          </div>
          <div aria-hidden className="pointer-events-none absolute inset-0 hidden xl:block">
            {[
              ['✓ ID verified', 'right-[34%] top-[16%]', 'text-emerald-700', 'bg-emerald-400', '0s'],
              ['₹80 held safely', 'right-[3%] top-[48%]', 'text-orange-700', 'bg-orange-400', '1.6s'],
              ['QR handover', 'right-[30%] top-[62%]', 'text-purple-700', 'bg-purple-400', '3.1s'],
            ].map(([t, pos, tc, dot, delay]) => (
              <span key={t} className={`glass absolute ${pos} flex animate-float items-center gap-2 rounded-full px-4 py-2 text-xs font-semibold ${tc}`} style={{ animationDelay: delay }}>
                <span className={`h-2 w-2 rounded-full ${dot}`} />{t}
              </span>
            ))}
          </div>
          <a href="#match" aria-label="See how it works" className="glass absolute inset-x-5 bottom-6 hidden grid-cols-3 gap-px overflow-hidden rounded-2xl text-left transition hover:shadow-[0_0_0_1px_rgba(37,99,235,0.35),0_24px_48px_-24px_rgba(30,64,175,0.4)] md:grid">
            {[['01', 'Post or find a trip', 'Travellers list journeys they are already making.'], ['02', 'Hand over with a code', 'A one-time code and photos confirm the exchange.'], ['03', 'Paid on delivery', 'Payment is held, then released once it arrives.']].map(([n, t, d], k) => (
              <span key={n} className="flex items-start gap-4 px-6 py-5">
                <span className="numeral text-3xl leading-none" style={{ '--nc': ['#2563eb', '#f97316', '#10b981'][k] } as React.CSSProperties}>{n}</span>
                <span>
                  <span className="block font-display text-base font-semibold text-slate-900">{t}</span>
                  <span className="mt-1 block text-xs leading-relaxed text-slate-500">{d}</span>
                </span>
              </span>
            ))}
          </a>
        </Section>

        {/* 01 match */}
        <Section id="match" stage={0.3} className="gap-10 lg:grid lg:grid-cols-[minmax(0,26rem)_1fr_minmax(0,25rem)]">
          <div>
            <Reveal><Eyebrow n="01">Match</Eyebrow></Reveal>
            <Reveal delay={80}><Heading>Travellers post trips. Parcels find them.</Heading></Reveal>
            <Reveal delay={160}><Lead>Hard rules decide who can carry it: route, date, capacity, verified identity. Then every candidate gets an explainable score, so you see exactly why one traveller ranks above another.</Lead></Reveal>
          </div>
          <div className="hidden lg:block" />
          <Reveal delay={200} className="mt-10 flex lg:mt-0 lg:justify-end"><MatchCard /></Reveal>
        </Section>

        {/* 02 handover */}
        <Section stage={0.57} className="gap-10 lg:grid lg:grid-cols-[minmax(0,25rem)_1fr_minmax(0,26rem)]">
          <Reveal className="order-2 mt-10 flex lg:order-1 lg:mt-0"><HandoverCard /></Reveal>
          <div className="order-2 hidden lg:block" />
          <div className="order-1 lg:order-3">
            <Reveal><Eyebrow n="02">Hand over</Eyebrow></Reveal>
            <Reveal delay={80}><Heading>A code. A photo. A handshake.</Heading></Reveal>
            <Reveal delay={160}><Lead>No sealed boxes and no warehouse. The traveller opens the parcel and checks it matches the description, you both add photos, and a one-time code confirms the exchange.</Lead></Reveal>
          </div>
        </Section>

        {/* 03 chain */}
        <Section stage={0.79} className="gap-10 lg:grid lg:grid-cols-[minmax(0,26rem)_1fr_minmax(0,25rem)]">
          <div>
            <Reveal><Eyebrow n="03">Chain of custody</Eyebrow></Reveal>
            <Reveal delay={80}><Heading>Every step, sealed into a chain.</Heading></Reveal>
            <Reveal delay={160}><Lead>Each event carries the fingerprint of the one before it, photos included. Change anything after the fact and the chain breaks, so disputes are settled on evidence, not on who shouts louder.</Lead></Reveal>
          </div>
          <div className="hidden lg:block" />
          <Reveal delay={200} className="mt-10 flex lg:mt-0 lg:justify-end"><ChainCard /></Reveal>
        </Section>

        {/* 04 paid */}
        <Section stage={0.93} className="gap-10 lg:grid lg:grid-cols-[minmax(0,25rem)_1fr_minmax(0,26rem)]">
          <Reveal className="order-2 mt-10 flex lg:order-1 lg:mt-0"><PayCard /></Reveal>
          <div className="order-2 hidden lg:block" />
          <div className="order-1 lg:order-3">
            <Reveal><Eyebrow n="04">Delivered, then paid</Eyebrow></Reveal>
            <Reveal delay={80}><Heading>The money moves when the parcel does.</Heading></Reveal>
            <Reveal delay={160}><Lead>Payment is held from the moment you book. It is released to the traveller after delivery, once the dispute window closes or you confirm. If something goes wrong, an admin can refund or split it.</Lead></Reveal>
          </div>
        </Section>

        {/* features */}
        <section data-stage={1} className="mx-auto max-w-6xl px-5 py-28">
          <Reveal><Eyebrow n="05">Why it is safe</Eyebrow></Reveal>
          <Reveal delay={80}><Heading>Built so strangers can trust each other.</Heading></Reveal>
          <div className="mt-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map(([t, d, icon], i0) => (
              <Reveal key={t} delay={(i0 % 3) * 90}>
                <TiltCard className="h-full">
                  <span className={`mb-4 grid h-11 w-11 place-items-center rounded-xl ${HUES[i0]}`}><Icon d={icon} /></span>
                  <h3 className="font-display text-lg font-semibold text-slate-900">{t}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-slate-500">{d}</p>
                </TiltCard>
              </Reveal>
            ))}
          </div>
          <Reveal delay={120}>
            <div className="mt-10 rounded-2xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
              <p className="font-semibold">Safety first</p>
              <p className="mt-1 text-amber-800">Cash, jewellery, medicines, alcohol, drugs, liquids and weapons are never allowed. Travellers must look inside every parcel before accepting it. ParcelShare does not insure goods, so keep declared values low. <Link to="/prohibited" className="font-medium underline">See the full list</Link>.</p>
            </div>
          </Reveal>
        </section>

        {/* routes marquee */}
        <section aria-label="Routes you can travel today" className="relative overflow-hidden border-y border-slate-900/10 bg-slate-900/[0.02] py-7 backdrop-blur-sm">
          <p className="mb-4 text-center text-[11px] font-semibold uppercase tracking-[0.3em] text-slate-400">Routes you can travel today</p>
          <div className="marquee flex w-max gap-4 whitespace-nowrap">
            {[...CITIES, ...CITIES].map((c, i) => (
              <span key={i} className="glass flex items-center gap-2.5 rounded-full px-5 py-2 font-display text-lg font-medium text-slate-800"><span className={`h-2 w-2 rounded-full ${DOTS[i % DOTS.length]}`} />{c}</span>
            ))}
          </div>
          <div className="marquee-rev mt-3 flex w-max gap-4 whitespace-nowrap">
            {[...CITIES].reverse().concat([...CITIES].reverse()).map((c, i) => (
              <span key={i} className="flex items-center gap-2.5 rounded-full border border-slate-900/10 bg-white/50 px-5 py-2 font-display text-lg font-medium text-slate-600"><span className={`h-2 w-2 rounded-full ${DOTS[(i + 3) % DOTS.length]}`} />{c}</span>
            ))}
          </div>
        </section>

        {/* final CTA */}
        <section data-stage={1} className="mx-auto flex min-h-[90svh] max-w-4xl flex-col items-center justify-center px-5 py-24 text-center">
          <Reveal><h2 className={`font-display text-5xl font-semibold leading-[1.02] tracking-tight text-slate-900 sm:text-6xl lg:text-7xl ${shadow}`}>Your next parcel is <span className="text-gradient">already travelling.</span></h2></Reveal>
          <Reveal delay={120}><p className={`mx-auto mt-6 max-w-xl text-base text-slate-600 sm:text-lg ${shadow}`}>Sign up in a minute. Post a trip to earn on a journey you were making anyway, or send something small today.</p></Reveal>
          <Reveal delay={220} className="mt-10 flex flex-wrap justify-center gap-3">
            <Magnetic><Link to="/register" className="btn-shine inline-block rounded-2xl bg-gradient-to-r from-indigo-500 via-violet-500 to-cyan-400 px-8 py-4 text-sm font-semibold text-white shadow-[0_10px_50px_-8px_rgba(37,99,235,1)] transition hover:brightness-110 active:scale-[0.98]">Create your account</Link></Magnetic>
            <Magnetic strength={0.18}><Link to="/login" className="inline-block rounded-2xl border border-slate-900/15 bg-slate-900/[0.05] px-8 py-4 text-sm font-semibold text-slate-900 backdrop-blur transition hover:bg-slate-900/10">Log in</Link></Magnetic>
          </Reveal>
        </section>
      </div>
    </div>
  )
}

export default function Home() {
  const { user } = useAuth()
  if (user) return <Navigate to="/trips" replace />
  return <Landing />
}
