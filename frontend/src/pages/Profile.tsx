import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from '../auth'
import { toast } from '../components/Toast'
import { Button, Card, Empty, ErrorBox, Field, PageTitle } from '../components/ui'
import { api, dateOnly, setToken } from '../lib'
import type { Review } from '../lib'

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <div className="text-center">
      <p className="font-display text-3xl font-semibold text-white">{value}</p>
      <p className="mt-1 text-xs uppercase tracking-wider text-slate-500">{label}</p>
    </div>
  )
}

export default function Profile() {
  const { user, refresh, logout } = useAuth()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [deletePw, setDeletePw] = useState('')
  const [note, setNote] = useState('')
  const [name, setName] = useState(user?.fullName ?? '')
  const [phone, setPhone] = useState(user?.phone ?? '')
  const [saved, setSaved] = useState(false)
  const [pw, setPw] = useState({ current: '', next: '' })
  const [pwDone, setPwDone] = useState(false)

  const reviews = useQuery({ queryKey: ['reviews'], queryFn: () => api<Review[]>('/me/reviews') })
  const request = useMutation({ mutationFn: () => api('/me/verification', { body: { note } }), onSuccess: () => refresh() })
  const save = useMutation({
    mutationFn: () => api('/me', { method: 'PATCH', body: { fullName: name, phone } }),
    onSuccess: async () => {
      await refresh()
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    },
  })
  const changePw = useMutation({
    mutationFn: () => api<{ token: string }>('/me/password', { body: pw }),
    onSuccess: (r) => {
      setToken(r.token) // every other device was just signed out; this one gets a fresh token and stays signed in
      setPw({ current: '', next: '' })
      setPwDone(true)
      setTimeout(() => setPwDone(false), 3000)
    },
  })

  const del = useMutation({
    mutationFn: () => api('/me/delete', { body: { password: deletePw } }),
    onSuccess: () => {
      logout() // signing out sends a protected page to the login screen, where the confirmation below is shown
      toast('Your account was deleted and your personal details removed.')
    },
  })

  async function download() {
    const data = await api('/me/export')
    const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
    const a = document.createElement('a')
    a.href = url
    a.download = 'parcelshare-my-data.json'
    a.click()
    URL.revokeObjectURL(url)
    toast('Your data was downloaded.')
  }

  if (!user) return null
  const onSave = (e: FormEvent) => { e.preventDefault(); save.mutate() }
  const onPw = (e: FormEvent) => { e.preventDefault(); changePw.mutate() }

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <PageTitle title={user.fullName} sub={user.email} />

      <Card>
        <div className="grid grid-cols-3 gap-3">
          <Stat value={`★ ${user.ratingAvg.toFixed(1)}`} label={`${user.ratingCount} ratings`} />
          <Stat value={`${user.reliabilityPercent}%`} label="reliability" />
          <Stat value={String(user.completedDeliveries)} label="deliveries" />
        </div>
      </Card>

      <div className="grid gap-5 md:grid-cols-2">
        <Card>
          <h2 className="mb-4 font-display text-lg font-semibold text-white">Your details</h2>
          <form onSubmit={onSave} className="space-y-4">
            <Field label="Full name"><input required maxLength={120} value={name} onChange={(e) => setName(e.target.value)} /></Field>
            <Field label="Phone" hint="Shared only after a booking is accepted."><input maxLength={20} value={phone} onChange={(e) => setPhone(e.target.value)} /></Field>
            <ErrorBox error={save.error} />
            <div className="flex items-center gap-3">
              <Button disabled={save.isPending || !name.trim()}>Save changes</Button>
              {saved && <span className="text-sm text-emerald-600">✓ Saved</span>}
            </div>
          </form>
        </Card>

        <Card>
          <h2 className="mb-4 font-display text-lg font-semibold text-white">Change password</h2>
          <form onSubmit={onPw} className="space-y-4">
            <Field label="Current password"><input type="password" required autoComplete="current-password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} /></Field>
            <Field label="New password" hint="At least 8 characters"><input type="password" required minLength={8} autoComplete="new-password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} /></Field>
            <ErrorBox error={changePw.error} />
            <div className="flex items-center gap-3">
              <Button variant="secondary" disabled={changePw.isPending}>Update password</Button>
              {pwDone && <span className="text-sm text-emerald-600">✓ Password changed</span>}
            </div>
          </form>
        </Card>
      </div>

      <Card>
        <h2 className="font-display text-lg font-semibold text-white">Traveller verification</h2>
        <p className="mt-1 text-sm text-slate-500">
          Status: <b className="text-slate-800">{user.travellerVerification}</b>. Only verified travellers can post trips.
        </p>
        {(user.travellerVerification === 'NONE' || user.travellerVerification === 'REJECTED') && (
          <div className="mt-4 space-y-4">
            <Field label="Tell us how to verify you" hint="E.g. your college or employer and ID type. An admin will contact you to check your ID. Do not paste ID numbers here.">
              <textarea rows={3} value={note} onChange={(e) => setNote(e.target.value)} />
            </Field>
            <ErrorBox error={request.error} />
            <Button disabled={!note.trim() || request.isPending} onClick={() => request.mutate()}>Request verification</Button>
          </div>
        )}
        {user.travellerVerification === 'PENDING' && <p className="mt-3 text-sm text-amber-700">Your request is waiting for admin review.</p>}
      </Card>

      <Card>
        <h2 className="font-display text-lg font-semibold text-white">Your data</h2>
        <p className="mt-1 text-sm text-slate-500">
          Download a copy of everything we hold about you, or delete your account. Deleting removes your name, email, phone and
          notifications. Bookings other people took part in stay in their history, but no longer identify you.
        </p>
        <div className="mt-4 flex flex-wrap gap-3">
          <Button variant="secondary" onClick={() => download().catch((e) => toast(String(e.message ?? e), 'err'))}>Download my data</Button>
          <Button variant="danger" onClick={() => setConfirmDelete(!confirmDelete)}>Delete my account</Button>
        </div>
        {confirmDelete && (
          <form onSubmit={(e) => { e.preventDefault(); del.mutate() }} className="mt-4 space-y-3 rounded-xl border border-red-200 bg-red-50 p-4">
            <p className="text-sm text-red-800">This cannot be undone. Enter your password to confirm.</p>
            <Field label="Password"><input type="password" required autoComplete="current-password" value={deletePw} onChange={(e) => setDeletePw(e.target.value)} /></Field>
            <ErrorBox error={del.error} />
            <Button variant="danger" disabled={!deletePw || del.isPending}>Permanently delete my account</Button>
          </form>
        )}
      </Card>

      <div>
        <h2 className="mb-3 font-display text-lg font-semibold text-white">What people said about you</h2>
        {!reviews.data?.length ? (
          <Empty>No reviews yet. They appear after your first completed booking.</Empty>
        ) : (
          <div className="space-y-3">
            {reviews.data.map((r, i) => (
              <Card key={i} className="!p-4">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-amber-700">{'★'.repeat(r.score)}<span className="text-slate-300">{'★'.repeat(5 - r.score)}</span></span>
                  <span className="text-xs text-slate-400">{r.fromName} · {dateOnly(r.createdAt)}</span>
                </div>
                {r.comment && <p className="mt-2 text-sm text-slate-700">“{r.comment}”</p>}
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
