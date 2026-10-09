import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, NavLink, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth'
import { fetchConfig } from '../lib'
import { useMotion } from '../motion'
import Logo from './Logo'
import NotificationBell from './NotificationBell'
import { Loading } from './ui'

const link = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-3 py-1.5 text-sm font-medium transition ${isActive ? 'bg-white/10 text-slate-900' : 'text-slate-500 hover:text-slate-900'}`

export default function Layout() {
  const { user, logout } = useAuth()
  const loc = useLocation()
  // the mobile menu is open only for the page it was opened on, so navigating closes it without an effect
  const [menuPath, setMenuPath] = useState<string | null>(null)
  const menu = menuPath === loc.pathname
  const [motion, setMotion] = useMotion()
  const config = useQuery({ queryKey: ['config'], queryFn: fetchConfig, staleTime: Infinity })
  const landing = loc.pathname === '/' && !user

  // pointer spotlight (CSS variables read by body::before); throttled to one update per frame
  useEffect(() => {
    let raf = 0
    const move = (e: PointerEvent) => {
      if (raf) return
      raf = requestAnimationFrame(() => {
        document.documentElement.style.setProperty('--mx', `${e.clientX}px`)
        document.documentElement.style.setProperty('--my', `${e.clientY}px`)
        raf = 0
      })
    }
    window.addEventListener('pointermove', move, { passive: true })
    return () => window.removeEventListener('pointermove', move)
  }, [])

  const items: [string, string, boolean?][] = [
    ['/trips', 'Trips'],
    ['/parcels/new', 'Send parcel'],
    ['/trips/new', 'Post trip'],
    ['/parcels', 'My parcels', true],
    ['/my-trips', 'My trips'],
    ['/bookings', 'Bookings'],
  ]

  return (
    <div className="relative z-[1] min-h-screen">
      {config.data?.demoMode && (
        <div className="relative z-50 bg-amber-100 px-4 py-1.5 text-center text-xs font-medium text-amber-800">
          Demo mode: payments are simulated and booking steps can be fast-forwarded.
        </div>
      )}
      <header className="sticky top-3 z-40 px-3">
        <div className="glass mx-auto flex max-w-6xl items-center justify-between rounded-2xl px-4 py-2.5">
          <Link to={user ? '/trips' : '/'} className="flex items-center gap-2.5 font-display text-lg font-semibold tracking-tight text-slate-900">
            <Logo />
            ParcelShare
          </Link>

          <nav className="hidden items-center gap-1 lg:flex" aria-label="Main">
            {user ? (
              <>
                {items.map(([to, label, end]) => <NavLink key={to} to={to} end={end} className={link}>{label}</NavLink>)}
                {user.role === 'ADMIN' && <NavLink to="/admin" className={link}>Admin</NavLink>}
              </>
            ) : (
              <>
                <NavLink to="/prohibited" className={link}>What you can send</NavLink>
                <NavLink to="/terms" className={link}>Terms</NavLink>
              </>
            )}
          </nav>

          <div className="flex items-center gap-2">
            {user ? (
              <>
                <NotificationBell />
                <NavLink to="/profile" className="hidden items-center gap-2 rounded-xl border border-white/10 bg-white/[0.04] py-1 pl-1 pr-3 text-sm font-medium text-slate-800 transition hover:bg-white/10 sm:flex">
                  <span className="grid h-7 w-7 place-items-center rounded-lg bg-gradient-to-br from-indigo-500 to-cyan-400 text-xs font-bold text-white">
                    {user.fullName.trim()[0]?.toUpperCase()}
                  </span>
                  {user.fullName.split(' ')[0]}
                </NavLink>
                <button onClick={logout} className="hidden cursor-pointer rounded-lg px-2.5 py-1.5 text-sm text-slate-500 transition hover:text-slate-900 sm:block">
                  Log out
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="rounded-lg px-3 py-1.5 text-sm font-medium text-slate-600 transition hover:text-slate-900">Log in</Link>
                <Link to="/register" className="rounded-xl bg-gradient-to-r from-indigo-500 to-cyan-400 px-4 py-2 text-sm font-semibold text-white shadow-[0_6px_24px_-6px_rgba(99,102,241,0.9)] transition hover:brightness-110">
                  Get started
                </Link>
              </>
            )}
            {user && (
              <button
                aria-label="Menu"
                aria-expanded={menu}
                onClick={() => setMenuPath(menu ? null : loc.pathname)}
                className="grid h-9 w-9 cursor-pointer place-items-center rounded-xl border border-white/10 bg-white/[0.04] lg:hidden"
              >
                <span className="relative block h-3 w-4">
                  <span className={`absolute left-0 top-0 h-[1.5px] w-4 bg-slate-800 transition ${menu ? 'translate-y-[5px] rotate-45' : ''}`} />
                  <span className={`absolute left-0 top-[5px] h-[1.5px] w-4 bg-slate-800 transition ${menu ? 'opacity-0' : ''}`} />
                  <span className={`absolute left-0 top-[10px] h-[1.5px] w-4 bg-slate-800 transition ${menu ? '-translate-y-[5px] -rotate-45' : ''}`} />
                </span>
              </button>
            )}
          </div>
        </div>

        {user && menu && (
          <div className="glass mx-auto mt-2 max-w-6xl rounded-2xl bg-[#0b0d1f]/95 p-2 lg:hidden">
            {items.map(([to, label, end]) => <NavLink key={to} to={to} end={end} className={({ isActive }) => `block ${link({ isActive })} py-2.5`}>{label}</NavLink>)}
            {user.role === 'ADMIN' && <NavLink to="/admin" className={({ isActive }) => `block ${link({ isActive })} py-2.5`}>Admin</NavLink>}
            <NavLink to="/profile" className={({ isActive }) => `block ${link({ isActive })} py-2.5`}>Profile</NavLink>
            <button onClick={logout} className="block w-full cursor-pointer rounded-lg px-3 py-2.5 text-left text-sm text-slate-500">Log out</button>
          </div>
        )}
      </header>

      {landing ? (
        <Outlet />
      ) : (
        <main key={loc.pathname} className="page-enter mx-auto max-w-6xl px-4 pb-16 pt-10">
          <Outlet />
        </main>
      )}

      <button
        onClick={() => setMotion(motion === 'full' ? 'calm' : 'full')}
        title={motion === 'full' ? 'Switch to calm motion (less movement)' : 'Switch to full motion (3D effects and animation)'}
        aria-pressed={motion === 'full'}
        className="glass fixed bottom-4 left-4 z-50 flex cursor-pointer items-center gap-2 rounded-full px-3.5 py-2 text-xs font-medium text-slate-700 transition hover:text-slate-900"
      >
        <span className={`h-2 w-2 rounded-full ${motion === 'full' ? 'bg-cyan-400 shadow-[0_0_10px_2px_rgba(34,211,238,0.8)]' : 'bg-slate-400'}`} />
        {motion === 'full' ? 'Full motion' : 'Calm motion'}
      </button>

      <footer className="relative z-[1] mx-auto max-w-6xl border-t border-white/10 px-4 py-8 text-center text-xs text-slate-500">
        <Link to="/prohibited" className="transition hover:text-slate-900">What you can send</Link>
        <span className="mx-2">·</span>
        <Link to="/terms" className="transition hover:text-slate-900">Terms of use</Link>
        <p className="mt-2">ParcelShare connects people. It does not carry, inspect or insure parcels.</p>
      </footer>
    </div>
  )
}

export function RequireAuth({ admin = false }: { admin?: boolean }) {
  const { user, loading } = useAuth()
  if (loading) return <Loading />
  if (!user) return <Navigate to="/login" replace />
  if (admin && user.role !== 'ADMIN') return <Navigate to="/" replace />
  return <Outlet />
}
