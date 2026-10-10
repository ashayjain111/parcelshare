import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth'
import { Button, Card, ErrorBox, Field, Loading } from '../components/ui'
import { api } from '../lib'
import type { User } from '../lib'

/**
 * Where DigiLocker sends the user back to. Exchanges the one-time code (through our server, which holds the secret)
 * and shows the outcome. The code can only be used once, so the guard below stops React's double-run in development.
 */
export function IdentityCallback() {
  const [params] = useSearchParams()
  const { refresh } = useAuth()
  const started = useRef(false)
  const [result, setResult] = useState<User | null>(null)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (started.current) return
    started.current = true
    const code = params.get('code')
    const state = params.get('state')
    if (!code || !state) {
      setError(new Error(params.get('error_description') ?? 'The verification was cancelled or did not complete.'))
      return
    }
    api<User>('/identity/complete', { body: { code, state } })
      .then((u) => {
        setResult(u)
        refresh()
      })
      .catch(setError)
  }, [params, refresh])

  return (
    <div className="mx-auto max-w-md">
      <Card className="text-center">
        {!result && !error && <Loading />}
        {error ? (
          <>
            <h1 className="font-display text-2xl font-semibold text-white">Verification did not complete</h1>
            <div className="mt-4 text-left"><ErrorBox error={error} /></div>
            <Link to="/profile" className="mt-5 inline-block text-sm text-indigo-600 hover:underline">Back to your profile</Link>
          </>
        ) : null}
        {result && (
          <>
            <div className="mx-auto grid h-14 w-14 place-items-center rounded-full bg-gradient-to-br from-emerald-400 to-cyan-400 text-2xl text-white shadow-[0_0_30px_4px_rgba(52,211,153,0.5)]">✓</div>
            <h1 className="mt-4 font-display text-2xl font-semibold text-white">Identity verified</h1>
            {result.travellerVerification === 'APPROVED' ? (
              <p className="mt-2 text-sm text-slate-500">You are now a verified traveller and can post trips straight away.</p>
            ) : (
              <p className="mt-2 text-sm text-amber-700">The name on your identity differs from your account name, so an admin will review it shortly.</p>
            )}
            <div className="mt-6 flex justify-center gap-3">
              {result.travellerVerification === 'APPROVED' && <Link to="/trips/new"><Button>Post a trip</Button></Link>}
              <Link to="/profile"><Button variant="secondary">Back to profile</Button></Link>
            </div>
          </>
        )}
      </Card>
    </div>
  )
}

/** A stand-in for DigiLocker's consent screen, shown only when the server runs in demo mode. */
export function DigiLockerDemo() {
  const [params] = useSearchParams()
  const { user } = useAuth()
  const nav = useNavigate()
  const [name, setName] = useState(user?.fullName ?? '')
  const state = params.get('state') ?? ''

  return (
    <div className="mx-auto max-w-md">
      <Card>
        <p className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-medium text-amber-800">
          DEMO SCREEN. This is not the real DigiLocker. It only exists so the flow can be shown without government credentials.
        </p>
        <h1 className="font-display text-2xl font-semibold text-white">ParcelShare wants to verify who you are</h1>
        <p className="mt-2 text-sm text-slate-500">
          In the real flow you sign in to DigiLocker and choose to share your verified name. ParcelShare never receives your
          Aadhaar number or any document.
        </p>
        <div className="mt-5">
          <Field label="Name to share (demo)" hint="Use your account name to be approved instantly, or a different name to see the admin-review path.">
            <input value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
        </div>
        <div className="mt-5 flex gap-3">
          <Button disabled={!name.trim() || !state} onClick={() => nav(`/identity/callback?code=${encodeURIComponent('demo:' + name.trim())}&state=${encodeURIComponent(state)}`)}>Allow</Button>
          <Button variant="secondary" onClick={() => nav('/profile')}>Cancel</Button>
        </div>
      </Card>
    </div>
  )
}
