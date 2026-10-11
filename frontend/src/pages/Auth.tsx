import { Suspense, lazy, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth'
import ErrorBoundary from '../components/ErrorBoundary'
import { Button, ErrorBox, Field } from '../components/ui'
import { api } from '../lib'
import { webglAvailable } from '../scene/env'

const MiniParcel = lazy(() => import('../scene/MiniParcel'))

/** Two-panel shell: a floating 3D parcel on the left (large screens, when WebGL is available) and the form on the right. */
function AuthShell({ title, sub, children, footer }: { title: string; sub: string; children: ReactNode; footer: ReactNode }) {
  const [gl] = useState(webglAvailable)
  return (
    <div className="mx-auto grid max-w-5xl items-center gap-x-6 gap-y-2 pt-2 lg:grid-cols-[1fr_28rem]">
      <div
        className="relative hidden h-[30rem] lg:col-start-1 lg:row-start-1 lg:block"
        aria-hidden
        style={{ maskImage: 'radial-gradient(closest-side, #000 58%, transparent 100%)', WebkitMaskImage: 'radial-gradient(closest-side, #000 58%, transparent 100%)' }}
      >
        {gl && (
          <ErrorBoundary fallback={null}>
            <Suspense fallback={null}>
              <MiniParcel />
            </Suspense>
          </ErrorBoundary>
        )}
      </div>
      <div className="hidden text-center lg:col-start-1 lg:row-start-2 lg:block">
        <p className="font-display text-2xl font-semibold text-slate-900">Send it with someone already going there.</p>
        <p className="mt-1 text-sm text-slate-500">Verified travellers. Payment held until delivery.</p>
      </div>
      <div className="glass glow-border rounded-3xl p-7 sm:p-9 lg:col-start-2 lg:row-start-1">
        <h1 className="text-3xl font-semibold tracking-tight text-slate-900">{title}</h1>
        <p className="mt-1.5 text-sm text-slate-500">{sub}</p>
        <div className="mt-7">{children}</div>
        <p className="mt-6 text-center text-sm text-slate-500">{footer}</p>
      </div>
    </div>
  )
}

export function Login() {
  const { login } = useAuth()
  const nav = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(email, password)
      nav('/trips')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Welcome back" sub="Log in to manage your trips and parcels." footer={<>New here? <Link to="/register" className="text-indigo-600 hover:underline">Create an account</Link></>}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Email"><input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
        <Field label="Password"><input type="password" required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
        <div className="text-right"><Link to="/forgot" className="text-xs text-slate-500 hover:text-indigo-600">Forgot password?</Link></div>
        <ErrorBox error={error} />
        <Button disabled={busy} className="w-full py-3">{busy ? 'Logging in…' : 'Log in'}</Button>
      </form>
    </AuthShell>
  )
}

export function Register() {
  const { register } = useAuth()
  const nav = useNavigate()
  const [v, setV] = useState({ fullName: '', email: '', phone: '', password: '' })
  const [agreed, setAgreed] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof v) => (e: { target: { value: string } }) => setV({ ...v, [k]: e.target.value })

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await register(v)
      nav('/trips')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Create your account" sub="Free to join. Send a parcel or earn on a trip you are already taking." footer={<>Already registered? <Link to="/login" className="text-indigo-600 hover:underline">Log in</Link></>}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Full name"><input required autoComplete="name" value={v.fullName} onChange={set('fullName')} /></Field>
        <Field label="Email"><input type="email" required autoComplete="email" value={v.email} onChange={set('email')} /></Field>
        <Field label="Phone" hint="Only shared with the other person after a booking is accepted.">
          <input autoComplete="tel" value={v.phone} onChange={set('phone')} />
        </Field>
        <Field label="Password" hint="At least 8 characters">
          <input type="password" required minLength={8} autoComplete="new-password" value={v.password} onChange={set('password')} />
        </Field>
        <label className="flex items-start gap-2.5 text-xs text-slate-600">
          <input type="checkbox" required checked={agreed} onChange={(e) => setAgreed(e.target.checked)} className="mt-0.5" />
          <span>I agree to the <Link className="text-indigo-600 hover:underline" to="/terms" target="_blank">Terms of use</Link> and the <Link className="text-indigo-600 hover:underline" to="/prohibited" target="_blank">prohibited items</Link> rules.</span>
        </label>
        <ErrorBox error={error} />
        <Button disabled={busy || !agreed} className="w-full py-3">{busy ? 'Creating…' : 'Create account'}</Button>
      </form>
    </AuthShell>
  )
}

export function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api('/auth/forgot', { body: { email } })
      setDone(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Forgot your password?" sub="Enter your email and we will send you a link to choose a new one." footer={<Link to="/login" className="text-indigo-600 hover:underline">Back to log in</Link>}>
      {done ? (
        <p className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          If that address has an account, a reset link is on its way. It is valid for one hour.
        </p>
      ) : (
        <form onSubmit={submit} className="space-y-4">
          <Field label="Email"><input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
          <ErrorBox error={error} />
          <Button disabled={busy} className="w-full py-3">{busy ? 'Sending…' : 'Send reset link'}</Button>
        </form>
      )}
    </AuthShell>
  )
}

export function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const nav = useNavigate()
  const [pw, setPw] = useState('')
  const [pw2, setPw2] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (pw !== pw2) {
      setError(new Error('The two passwords do not match'))
      return
    }
    setBusy(true)
    setError(null)
    try {
      await api('/auth/reset', { body: { token, password: pw } })
      setDone(true)
      setTimeout(() => nav('/login'), 1800)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Choose a new password" sub="Pick something at least 8 characters long." footer={<Link to="/login" className="text-indigo-600 hover:underline">Back to log in</Link>}>
      {!token ? (
        <ErrorBox error={new Error('This link is missing its token. Use the link from your email.')} />
      ) : done ? (
        <p className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">Password updated. Taking you to log in…</p>
      ) : (
        <form onSubmit={submit} className="space-y-4">
          <Field label="New password"><input type="password" required minLength={8} autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} /></Field>
          <Field label="Repeat new password"><input type="password" required minLength={8} autoComplete="new-password" value={pw2} onChange={(e) => setPw2(e.target.value)} /></Field>
          <ErrorBox error={error} />
          <Button disabled={busy} className="w-full py-3">{busy ? 'Saving…' : 'Update password'}</Button>
        </form>
      )}
    </AuthShell>
  )
}
