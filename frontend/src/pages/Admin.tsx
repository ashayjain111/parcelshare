import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, Card, Empty, ErrorBox, Field, Loading, PageTitle, StatusBadge, Timeline } from '../components/ui'
import { api, dateTime, kg, rupees } from '../lib'
import type { AdminUser, BookingView, DisputeView, EventView, ParcelView, PolicyCategory } from '../lib'

const TABS = ['Overview', 'Verifications', 'Parcels', 'Disputes', 'Users', 'Bookings', 'Policy', 'Audit'] as const
type Tab = (typeof TABS)[number]

export default function Admin() {
  const [tab, setTab] = useState<Tab>('Overview')
  return (
    <>
      <PageTitle title="Admin" sub="Moderation, disputes and platform health." />
      <div className="mb-4 flex flex-wrap gap-1">
        {TABS.map((t) => (
          <button key={t} onClick={() => setTab(t)} className={`cursor-pointer rounded-lg px-3.5 py-1.5 text-sm font-medium transition ${tab === t ? 'bg-gradient-to-r from-indigo-500 to-cyan-400 text-white shadow-[0_6px_20px_-6px_rgba(37,99,235,0.9)]' : 'border border-slate-900/10 bg-slate-900/[0.04] text-slate-600 hover:bg-slate-900/10'}`}>{t}</button>
        ))}
      </div>
      {tab === 'Overview' && <Overview />}
      {tab === 'Verifications' && <Verifications />}
      {tab === 'Parcels' && <ParcelReview />}
      {tab === 'Disputes' && <Disputes />}
      {tab === 'Users' && <Users />}
      {tab === 'Bookings' && <Bookings />}
      {tab === 'Policy' && <Policy />}
      {tab === 'Audit' && <Audit />}
    </>
  )
}

function Overview() {
  const q = useQuery({ queryKey: ['admin', 'stats'], queryFn: () => api<Record<string, number>>('/admin/stats') })
  if (q.isLoading) return <Loading />
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {Object.entries(q.data ?? {}).map(([k, v]) => (
        <Card key={k}><p className="text-3xl font-semibold">{v}</p><p className="text-sm text-slate-500">{k.replace(/([A-Z])/g, ' $1').toLowerCase()}</p></Card>
      ))}
    </div>
  )
}

function Verifications() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['admin', 'verifications'], queryFn: () => api<AdminUser[]>('/admin/verifications') })
  const decide = useMutation({
    mutationFn: (v: { id: number; approve: boolean }) => api(`/admin/verifications/${v.id}/decision`, { body: { approve: v.approve } }),
    onSuccess: () => qc.invalidateQueries(),
  })
  if (q.isLoading) return <Loading />
  if (!q.data?.length) return <Empty>No pending verification requests.</Empty>
  return (
    <div className="space-y-3">
      {q.data.map((u) => (
        <Card key={u.id}>
          <p className="font-medium">{u.fullName} <span className="text-sm font-normal text-slate-500">{u.email} · {u.phone}</span></p>
          <p className="mt-1 text-sm text-slate-600">“{u.verificationNote}”</p>
          <p className="mt-1 text-xs text-slate-500">Check their ID out-of-band (e.g. video call). Do not store ID images.</p>
          <div className="mt-2 flex gap-2">
            <Button onClick={() => decide.mutate({ id: u.id, approve: true })}>Approve</Button>
            <Button variant="danger" onClick={() => decide.mutate({ id: u.id, approve: false })}>Reject</Button>
          </div>
        </Card>
      ))}
      <ErrorBox error={decide.error} />
    </div>
  )
}

function ParcelReview() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['admin', 'parcels'], queryFn: () => api<ParcelView[]>('/admin/parcels/pending') })
  const review = useMutation({
    mutationFn: (v: { id: number; approve: boolean }) => api(`/admin/parcels/${v.id}/review`, { body: { approve: v.approve } }),
    onSuccess: () => qc.invalidateQueries(),
  })
  if (q.isLoading) return <Loading />
  if (!q.data?.length) return <Empty>No parcels awaiting review.</Empty>
  return (
    <div className="space-y-3">
      {q.data.map((p) => (
        <Card key={p.id}>
          <p className="font-medium">{p.publicCode} · {p.categoryName} · {kg(p.weightGrams)} · {rupees(p.declaredValuePaise)}</p>
          <p className="text-sm text-slate-600">{p.origin.name} → {p.destination.name}: {p.description}</p>
          <p className="text-xs text-slate-500">Sender: {p.sender.name} (★ {p.sender.ratingAvg.toFixed(1)}) · {p.flagReason}</p>
          <div className="mt-2 flex gap-2">
            <Button onClick={() => review.mutate({ id: p.id, approve: true })}>Approve</Button>
            <Button variant="danger" onClick={() => review.mutate({ id: p.id, approve: false })}>Reject</Button>
          </div>
        </Card>
      ))}
    </div>
  )
}

function Disputes() {
  const [openOnly, setOpenOnly] = useState(true)
  const q = useQuery({ queryKey: ['admin', 'disputes', openOnly], queryFn: () => api<DisputeView[]>(`/admin/disputes?openOnly=${openOnly}`) })
  return (
    <>
      <label className="mb-3 flex items-center gap-2 text-sm"><input type="checkbox" checked={openOnly} onChange={(e) => setOpenOnly(e.target.checked)} /> Open only</label>
      {q.isLoading ? <Loading /> : !q.data?.length ? <Empty>No disputes.</Empty> : (
        <div className="space-y-3">{q.data.map((d) => <DisputeCard key={d.id} d={d} />)}</div>
      )}
    </>
  )
}

function DisputeCard({ d }: { d: DisputeView }) {
  const qc = useQueryClient()
  const [show, setShow] = useState(false)
  const [share, setShare] = useState('')
  const [note, setNote] = useState('')
  const events = useQuery({ queryKey: ['events', String(d.bookingId)], enabled: show, queryFn: () => api<EventView[]>(`/bookings/${d.bookingId}/events`) })
  const resolve = useMutation({
    mutationFn: (resolution: 'RELEASE' | 'REFUND' | 'PARTIAL') =>
      api(`/admin/disputes/${d.id}/resolve`, { body: { resolution, note, travellerSharePaise: resolution === 'PARTIAL' ? Math.round(Number(share) * 100) : null } }),
    onSuccess: () => qc.invalidateQueries(),
  })
  return (
    <Card>
      <div className="flex items-center justify-between">
        <p className="font-medium">{d.parcelCode} · {rupees(d.amountPaise)} · {d.senderName} ⇄ {d.travellerName}</p>
        <StatusBadge status={d.status} />
      </div>
      <p className="mt-1 text-sm text-slate-700"><b>{d.raisedByRole}</b> ({dateTime(d.createdAt)}): {d.reason}</p>
      <div className="mt-2 flex items-center gap-3">
        <Button variant="ghost" onClick={() => setShow(!show)}>{show ? 'Hide' : 'Review'} evidence timeline</Button>
        <Link to={`/bookings/${d.bookingId}`} className="text-sm text-indigo-600">Open booking</Link>
      </div>
      {show && events.data && <div className="mt-3 rounded-lg bg-slate-50 p-3"><Timeline events={events.data} /></div>}
      {d.status === 'OPEN' ? (
        <div className="mt-3 space-y-2 border-t border-slate-100 pt-3">
          <Field label="Admin note"><input value={note} onChange={(e) => setNote(e.target.value)} /></Field>
          <div className="flex flex-wrap items-end gap-2">
            <Button onClick={() => resolve.mutate('RELEASE')}>Release to traveller</Button>
            <Button variant="danger" onClick={() => resolve.mutate('REFUND')}>Refund sender</Button>
            <input className="!w-28" type="number" placeholder="₹ to traveller" value={share} onChange={(e) => setShare(e.target.value)} />
            <Button variant="secondary" disabled={!share} onClick={() => resolve.mutate('PARTIAL')}>Partial split</Button>
          </div>
          <ErrorBox error={resolve.error} />
        </div>
      ) : <p className="mt-2 text-sm text-slate-600">Resolved: {d.resolution}{d.adminNote ? ` · ${d.adminNote}` : ''}</p>}
    </Card>
  )
}

function Users() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['admin', 'users'], queryFn: () => api<AdminUser[]>('/admin/users') })
  const toggle = useMutation({
    mutationFn: (v: { id: number; suspend: boolean }) => api(`/admin/users/${v.id}/${v.suspend ? 'suspend' : 'reactivate'}`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries(),
  })
  if (q.isLoading) return <Loading />
  return (
    <div className="space-y-2">
      {q.data?.map((u) => (
        <Card key={u.id} className="flex flex-wrap items-center justify-between gap-2">
          <div className="text-sm">
            <p className="font-medium">{u.fullName} <span className="font-normal text-slate-500">{u.email}</span></p>
            <p className="text-xs text-slate-500">{u.role} · verification {u.travellerVerification} · ★ {u.ratingAvg} ({u.ratingCount}) · {u.completedDeliveries} done / {u.cancelledCount} cancelled / {u.disputedCount} disputed</p>
          </div>
          <div className="flex items-center gap-2">
            <StatusBadge status={u.status} />
            {u.role !== 'ADMIN' && (u.status === 'ACTIVE'
              ? <Button variant="danger" onClick={() => toggle.mutate({ id: u.id, suspend: true })}>Suspend</Button>
              : <Button variant="secondary" onClick={() => toggle.mutate({ id: u.id, suspend: false })}>Reactivate</Button>)}
          </div>
        </Card>
      ))}
    </div>
  )
}

function Bookings() {
  const q = useQuery({ queryKey: ['admin', 'bookings'], queryFn: () => api<BookingView[]>('/admin/bookings') })
  if (q.isLoading) return <Loading />
  if (!q.data?.length) return <Empty>No bookings yet.</Empty>
  return (
    <div className="space-y-2">
      {q.data.map((b) => (
        <Link key={b.id} to={`/bookings/${b.id}`}>
          <Card className="flex items-center justify-between hover:border-indigo-300">
            <p className="text-sm">{b.parcel.publicCode} · {b.trip.origin.name} → {b.trip.destination.name} · {b.sender.name} / {b.traveller.name} · {rupees(b.pricePaise)}</p>
            <div className="flex gap-2"><StatusBadge status={b.status} /><StatusBadge status={b.paymentStatus} /></div>
          </Card>
        </Link>
      ))}
    </div>
  )
}

interface AuditRow { id: number; actorId: number | null; action: string; entityType: string; entityId: number; detail: string; createdAt: string }

function Audit() {
  const q = useQuery({ queryKey: ['admin', 'audit'], queryFn: () => api<AuditRow[]>('/admin/audit') })
  if (q.isLoading) return <Loading />
  if (!q.data?.length) return <Empty>No audit entries yet.</Empty>
  return (
    <Card className="overflow-x-auto p-0">
      <table className="w-full text-left text-sm">
        <thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="px-3 py-2">When</th><th className="px-3 py-2">Actor</th><th className="px-3 py-2">Action</th><th className="px-3 py-2">Detail</th></tr></thead>
        <tbody>
          {q.data.map((r) => (
            <tr key={r.id} className="border-t border-slate-100"><td className="px-3 py-2">{dateTime(r.createdAt)}</td><td className="px-3 py-2">{r.actorId ?? 'system'}</td><td className="px-3 py-2 font-mono text-xs">{r.action}</td><td className="px-3 py-2 text-slate-600">{r.detail}</td></tr>
          ))}
        </tbody>
      </table>
    </Card>
  )
}

function CategoryRow({ c, onSaved }: { c: PolicyCategory | null; onSaved: () => void }) {
  const [v, setV] = useState({
    name: c?.name ?? '', tier: c?.tier ?? 'ALLOWED', kg: String((c?.maxGrams ?? 2000) / 1000),
    rs: String((c?.maxValuePaise ?? 300000) / 100), active: c?.active ?? true,
  })
  const save = useMutation({
    mutationFn: () => {
      const body = { name: v.name, tier: v.tier, maxGrams: Math.round(Number(v.kg) * 1000), maxValuePaise: Math.round(Number(v.rs) * 100), active: v.active }
      return c ? api(`/admin/policy/categories/${c.id}`, { method: 'PUT', body }) : api('/admin/policy/categories', { body })
    },
    onSuccess: () => { onSaved(); if (!c) setV({ name: '', tier: 'ALLOWED', kg: '2', rs: '3000', active: true }) },
  })
  const prohibited = v.tier === 'PROHIBITED'
  return (
    <div className="grid items-end gap-2 sm:grid-cols-12">
      <div className="sm:col-span-4"><input placeholder="Category name" value={v.name} onChange={(e) => setV({ ...v, name: e.target.value })} /></div>
      <div className="sm:col-span-2">
        <select value={v.tier} onChange={(e) => setV({ ...v, tier: e.target.value as PolicyCategory['tier'] })}>
          <option value="ALLOWED">Allowed</option><option value="RESTRICTED">Restricted</option><option value="PROHIBITED">Prohibited</option>
        </select>
      </div>
      <div className="sm:col-span-1"><input disabled={prohibited} type="number" step="any" title="Max kg" value={prohibited ? '' : v.kg} onChange={(e) => setV({ ...v, kg: e.target.value })} /></div>
      <div className="sm:col-span-2"><input disabled={prohibited} type="number" title="Max declared value in rupees" value={prohibited ? '' : v.rs} onChange={(e) => setV({ ...v, rs: e.target.value })} /></div>
      <label className="flex items-center gap-1 text-xs sm:col-span-1"><input type="checkbox" checked={v.active} onChange={(e) => setV({ ...v, active: e.target.checked })} /> active</label>
      <div className="sm:col-span-2"><Button className="w-full" disabled={!v.name.trim() || save.isPending} onClick={() => save.mutate()}>{c ? 'Save' : 'Add'}</Button></div>
      {save.error && <div className="sm:col-span-12"><ErrorBox error={save.error} /></div>}
    </div>
  )
}

function Policy() {
  const qc = useQueryClient()
  const cats = useQuery({ queryKey: ['admin', 'policy', 'categories'], queryFn: () => api<PolicyCategory[]>('/admin/policy/categories') })
  const words = useQuery({ queryKey: ['admin', 'policy', 'keywords'], queryFn: () => api<{ id: number; word: string }[]>('/admin/policy/keywords') })
  const [word, setWord] = useState('')
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin'] })
  const add = useMutation({
    mutationFn: () => api('/admin/policy/keywords', { body: { word } }),
    onSuccess: () => { setWord(''); refresh() },
  })
  const remove = useMutation({ mutationFn: (id: number) => api(`/admin/policy/keywords/${id}`, { method: 'DELETE' }), onSuccess: refresh })
  if (cats.isLoading || words.isLoading) return <Loading />
  return (
    <div className="space-y-5">
      <Card>
        <h2 className="mb-1 font-semibold">Parcel categories</h2>
        <p className="mb-3 text-xs text-slate-500">Tier decides what happens: allowed goes live, restricted waits for review, prohibited is refused. Changes apply immediately and are audit-logged. Columns: name, tier, max kg, max declared value (₹).</p>
        <div className="space-y-3">
          {cats.data?.map((c) => <CategoryRow key={`${c.id}-${c.name}-${c.tier}-${c.maxGrams}-${c.maxValuePaise}-${c.active}`} c={c} onSaved={refresh} />)}
          <div className="border-t border-slate-100 pt-3"><p className="mb-1 text-xs font-medium text-slate-600">Add a category</p><CategoryRow c={null} onSaved={refresh} /></div>
        </div>
      </Card>
      <Card>
        <h2 className="mb-1 font-semibold">Blocked words</h2>
        <p className="mb-3 text-xs text-slate-500">A parcel description containing any of these words is rejected.</p>
        <div className="mb-3 flex flex-wrap gap-2">
          {words.data?.map((k) => (
            <span key={k.id} className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 text-sm">
              {k.word}<button className="text-slate-400 hover:text-red-600" title="Remove" onClick={() => remove.mutate(k.id)}>×</button>
            </span>
          ))}
        </div>
        <div className="flex gap-2"><input placeholder="Add a word" value={word} onChange={(e) => setWord(e.target.value)} /><Button disabled={!word.trim()} onClick={() => add.mutate()}>Add</Button></div>
        <div className="mt-2"><ErrorBox error={add.error ?? remove.error} /></div>
      </Card>
    </div>
  )
}
