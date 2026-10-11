import { toast } from '../components/Toast'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { QRCodeSVG } from 'qrcode.react'
import { Suspense, lazy, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import EvidencePanel from '../components/Evidence'
import ProgressTrack from '../components/ProgressTrack'
import { Button, Card, Empty, ListSkeleton, ErrorBox, Field, Loading, PageTitle, Person, StatusBadge, Timeline } from '../components/ui'
import { api, dateTime, fetchConfig, kg, rupees } from '../lib'
import type { BookingView, EventView } from '../lib'

const NEEDS_ME = new Set(['ACCEPT', 'REJECT', 'CONFIRM_HANDOVER', 'START_JOURNEY', 'COMPLETE_DELIVERY', 'GENERATE_HANDOVER_CODE', 'CONFIRM_RECEIPT', 'RATE', 'RETRY_DELIVERY'])

export function BookingList() {
  const q = useQuery({ queryKey: ['bookings'], queryFn: () => api<BookingView[]>('/bookings/mine'), refetchInterval: 15000 })
  return (
    <>
      <PageTitle title="Bookings" sub="Every parcel you are sending or carrying." />
      {q.isLoading ? <ListSkeleton /> : !q.data?.length ? <Empty>No bookings yet.</Empty> : (
        <div className="stagger space-y-3">
          {q.data.map((b) => (
            <Link key={b.id} to={`/bookings/${b.id}`}>
              <Card className="hover:border-indigo-300">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-semibold">{b.parcel.publicCode} · {b.trip.origin.name} → {b.trip.destination.name}</p>
                  <div className="flex items-center gap-2">
                    {b.actions.some((a) => NEEDS_ME.has(a)) && <span className="rounded-full bg-gradient-to-r from-indigo-500 to-cyan-400 px-2.5 py-0.5 text-xs font-semibold text-white shadow-[0_0_14px_-2px_rgba(56,189,248,0.7)]">Action needed</span>}
                    <StatusBadge status={b.status} />
                  </div>
                </div>
                <p className="text-sm text-slate-600">
                  You are the {b.viewerRole.toLowerCase()} · {dateTime(b.trip.departsAt)} · {b.viewerRole === 'TRAVELLER' ? `you earn ${rupees(b.travellerPayoutPaise)}` : `you pay ${rupees(b.pricePaise)}`}
                </p>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </>
  )
}

const NEXT_STEP: Record<string, string> = {
  REQUESTED: 'traveller accepts',
  ACCEPTED: 'handover',
  HANDED_OVER: 'journey starts',
  IN_TRANSIT: 'arrival, then delivery',
  DELIVERY_FAILED: 'retry delivery',
  DELIVERED: 'sender releases payment',
}

// The camera/QR code is only needed on the handover screen, so keep it out of the main bundle.
const QrScanner = lazy(() => import('../components/QrScanner'))

type Panel = null | 'handover-confirm' | 'deliver' | 'failed' | 'dispute' | 'rate'

export function BookingDetail() {
  const { id } = useParams()
  const qc = useQueryClient()
  const booking = useQuery({ queryKey: ['booking', id], queryFn: () => api<BookingView>(`/bookings/${id}`), refetchInterval: 10000 })
  const events = useQuery({ queryKey: ['events', id], queryFn: () => api<EventView[]>(`/bookings/${id}/events`), refetchInterval: 10000 })
  const config = useQuery({ queryKey: ['config'], queryFn: fetchConfig, staleTime: Infinity })
  const [panel, setPanel] = useState<Panel>(null)
  const [handover, setHandover] = useState<{ code: string; expiresAt: string } | null>(null)
  const [integrity, setIntegrity] = useState<string | null>(null)
  const [scanning, setScanning] = useState(false)
  const [scanMsg, setScanMsg] = useState<string | null>(null)
  const [f, setF] = useState({ code: '', checked: false, note: '', reason: '', score: 5, comment: '' })

  const done = () => { qc.invalidateQueries(); setPanel(null); toast('Done. The booking was updated.') }
  const post = (path: string, body?: unknown) => api(`/bookings/${id}/${path}`, { method: 'POST', body })
  const act = useMutation({ mutationFn: (fn: () => Promise<unknown>) => fn(), onSuccess: done })

  if (booking.isLoading) return <Loading />
  if (booking.error || !booking.data) return <ErrorBox error={booking.error} />
  const b = booking.data
  const has = (a: string) => b.actions.includes(a)
  const run = (fn: () => Promise<unknown>) => () => act.mutate(fn)
  const counterpart = b.viewerRole === 'SENDER' ? b.traveller : b.sender

  async function verify() {
    const r = await api<{ valid: boolean; events: number }>(`/bookings/${id}/events/verify`)
    setIntegrity(r.valid ? `✓ History intact (${r.events} events, hash chain verified)` : '⚠ History has been altered!')
  }

  return (
    <>
      <PageTitle
        title={`${b.parcel.publicCode}: ${b.trip.origin.name} → ${b.trip.destination.name}`}
        sub={`${b.parcel.categoryName} · ${kg(b.parcel.weightGrams)} · you are the ${b.viewerRole.toLowerCase()}`}
        right={<StatusBadge status={b.status} />}
      />

      <ProgressTrack status={b.status} />

      <div className="grid gap-4 md:grid-cols-5">
        <div className="space-y-4 md:col-span-3">
          <Card>
            <p className="text-sm text-slate-600">{b.parcel.description}</p>
            <div className="mt-3 space-y-1">
              <p><Person u={b.sender} role="sender" /></p>
              <p><Person u={b.traveller} role="traveller" /></p>
            </div>
            <p className="mt-3 text-sm text-slate-600">Departs {dateTime(b.trip.departsAt)} by {b.trip.travelMode.toLowerCase()}.</p>
            {b.counterpartPhone && <p className="mt-1 text-sm">Contact {counterpart.name}: <a className="text-indigo-600" href={`tel:${b.counterpartPhone}`}>{b.counterpartPhone}</a></p>}
          </Card>

          <Card>
            <h3 className="mb-2 font-semibold">Payment</h3>
            <div className="flex items-center justify-between text-sm"><span>Total paid by sender</span><span>{rupees(b.pricePaise)}</span></div>
            <div className="flex items-center justify-between text-sm text-slate-500"><span>Platform commission</span><span>− {rupees(b.commissionPaise)}</span></div>
            <div className="flex items-center justify-between border-t border-slate-100 pt-1 text-sm font-medium"><span>Traveller earns</span><span>{rupees(b.travellerPayoutPaise)}</span></div>
            <p className="mt-2 text-xs text-slate-500">Status: <StatusBadge status={b.paymentStatus} />{b.releaseAt && b.paymentStatus === 'HELD' ? ` · auto-releases ${dateTime(b.releaseAt)} unless disputed` : ''}</p>
            <p className="mt-1 text-xs text-slate-400">Demo mode: payments are simulated, no real money moves.</p>
          </Card>

          <EvidencePanel bookingId={b.id} role={b.viewerRole} status={b.status} />

          {/* Sender-only secrets */}
          {b.viewerRole === 'SENDER' && b.deliveryCode && (
            <Card className="border-indigo-200 bg-indigo-50">
              <p className="text-sm font-semibold text-indigo-900">Delivery code</p>
              <p className="my-1 font-mono text-3xl tracking-widest text-indigo-900">{b.deliveryCode}</p>
              <p className="text-xs text-indigo-800">Share this with the recipient (or send them the link below). They read it to the traveller when the parcel arrives.</p>
              {b.recipientLink && <p className="mt-2 break-all text-xs"><a className="text-indigo-700 underline" target="_blank" rel="noreferrer" href={b.recipientLink}>{window.location.origin}{b.recipientLink}</a></p>}
            </Card>
          )}

          {handover && has('GENERATE_HANDOVER_CODE') && (
            <Card className="border-emerald-200 bg-emerald-50 text-center">
              <p className="text-sm font-semibold text-emerald-900">Handover code (valid 15 min, single use)</p>
              <p className="my-1 font-mono text-4xl tracking-widest text-emerald-900">{handover.code}</p>
              <div className="my-2 flex justify-center"><QRCodeSVG value={`parcelshare:handover:${b.id}:${handover.code}`} size={128} /></div>
              <p className="text-xs text-emerald-800">Show this to the traveller <em>after</em> they have inspected the contents. Take photos of the item for your records.</p>
            </Card>
          )}

          {act.error && <ErrorBox error={act.error} />}

          {config.data?.demoMode && !['CANCELLED', 'REJECTED', 'RESOLVED', 'DISPUTED'].includes(b.status)
            && !(b.status === 'DELIVERED' && b.paymentStatus !== 'HELD') && (
            <Card className="border-amber-300 bg-amber-50">
              <p className="text-sm font-semibold text-amber-900">Demo helper</p>
              <p className="mb-2 text-xs text-amber-800">Plays whoever needs to act next (traveller, sender or recipient), using the real rules, codes and events.</p>
              <Button disabled={act.isPending} onClick={() => act.mutate(() => api(`/demo/bookings/${id}/advance`, { method: 'POST' }))}>
                Simulate next step ({NEXT_STEP[b.status] ?? 'continue'})
              </Button>
            </Card>
          )}

          {b.actions.length > 0 && (
            <Card>
              <h3 className="mb-3 font-semibold">What you can do now</h3>
              <div className="flex flex-wrap gap-2">
                {has('ACCEPT') && <Button onClick={run(() => post('accept'))}>Accept booking</Button>}
                {has('REJECT') && <Button variant="secondary" onClick={run(() => post('reject'))}>Decline</Button>}
                {has('GENERATE_HANDOVER_CODE') && <Button onClick={async () => setHandover(await post('handover/code') as { code: string; expiresAt: string })}>Hand over parcel (generate code)</Button>}
                {has('CONFIRM_HANDOVER') && <Button onClick={() => setPanel('handover-confirm')}>Confirm handover</Button>}
                {has('START_JOURNEY') && <Button onClick={run(() => post('start'))}>Start journey</Button>}
                {has('MARK_ARRIVED') && <Button variant="secondary" onClick={run(() => post('arrive'))}>I have arrived</Button>}
                {has('COMPLETE_DELIVERY') && <Button onClick={() => setPanel('deliver')}>Complete delivery</Button>}
                {has('DELIVERY_FAILED') && <Button variant="secondary" onClick={() => setPanel('failed')}>Recipient unavailable</Button>}
                {has('RETRY_DELIVERY') && <Button onClick={run(() => post('retry-delivery'))}>Retry delivery</Button>}
                {has('CONFIRM_RECEIPT') && <Button onClick={run(() => post('confirm-receipt'))}>Confirm received & release payment</Button>}
                {has('RATE') && <Button variant="secondary" onClick={() => setPanel('rate')}>Rate {counterpart.name}</Button>}
                {has('CANCEL') && <Button variant="danger" onClick={() => { if (confirm(b.status === 'ACCEPTED' && b.viewerRole === 'SENDER' ? 'Cancelling after acceptance costs a 10% fee. Continue?' : 'Cancel this booking?')) act.mutate(() => post('cancel')) }}>Cancel</Button>}
                {has('RAISE_DISPUTE') && <Button variant="danger" onClick={() => setPanel('dispute')}>Raise dispute</Button>}
              </div>

              {panel === 'handover-confirm' && (
                <div className="mt-4 space-y-3 border-t border-slate-100 pt-3">
                  <Field label="Code from the sender"><input inputMode="numeric" maxLength={6} value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} /></Field>
                  {scanning ? (
                    <Suspense fallback={<Loading />}><QrScanner onClose={() => setScanning(false)} onResult={(text) => {
                      const m = /^parcelshare:handover:(\d+):(\d{6})$/.exec(text.trim())
                      setScanning(false)
                      if (!m || Number(m[1]) !== b.id) { setScanMsg('That QR code is not for this booking.'); return }
                      setScanMsg(null)
                      setF((prev) => ({ ...prev, code: m[2] }))
                    }} /></Suspense>
                  ) : <Button variant="secondary" onClick={() => { setScanMsg(null); setScanning(true) }}>Scan sender's QR code</Button>}
                  {scanMsg && <p className="text-sm text-red-700">{scanMsg}</p>}
                  <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={f.checked} onChange={(e) => setF({ ...f, checked: e.target.checked })} className="mt-0.5" />
                    <span>I have opened and inspected the parcel. The contents match the description: <em>{b.parcel.description}</em>. They contain nothing illegal or prohibited.</span></label>
                  <Field label="Note (optional)"><input value={f.note} onChange={(e) => setF({ ...f, note: e.target.value })} /></Field>
                  <Button disabled={!f.checked || f.code.length < 6} onClick={run(() => post('handover/confirm', { code: f.code, contentsChecked: f.checked, note: f.note }))}>Confirm I received the parcel</Button>
                </div>
              )}
              {panel === 'deliver' && (
                <div className="mt-4 space-y-3 border-t border-slate-100 pt-3">
                  <Field label="Delivery code from the recipient"><input inputMode="numeric" maxLength={6} value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} /></Field>
                  <Button disabled={f.code.length < 6} onClick={run(() => post('deliver', { code: f.code }))}>Confirm delivery</Button>
                </div>
              )}
              {panel === 'failed' && (
                <div className="mt-4 space-y-3 border-t border-slate-100 pt-3">
                  <Field label="What happened?"><input value={f.note} onChange={(e) => setF({ ...f, note: e.target.value })} /></Field>
                  <Button onClick={run(() => post('delivery-failed', { note: f.note }))}>Report failed delivery</Button>
                </div>
              )}
              {panel === 'dispute' && (
                <div className="mt-4 space-y-3 border-t border-slate-100 pt-3">
                  <Field label="Describe the problem" hint="The payment is frozen and an admin reviews the booking's event history."><textarea rows={3} value={f.reason} onChange={(e) => setF({ ...f, reason: e.target.value })} /></Field>
                  <Button variant="danger" disabled={!f.reason.trim()} onClick={run(() => post('dispute', { reason: f.reason }))}>Submit dispute</Button>
                </div>
              )}
              {panel === 'rate' && (
                <div className="mt-4 space-y-3 border-t border-slate-100 pt-3">
                  <Field label="Rating">
                    <select value={f.score} onChange={(e) => setF({ ...f, score: Number(e.target.value) })}>
                      {[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{'★'.repeat(n)}</option>)}
                    </select>
                  </Field>
                  <Field label="Comment (optional)"><input value={f.comment} onChange={(e) => setF({ ...f, comment: e.target.value })} /></Field>
                  <Button onClick={run(() => post('rating', { score: f.score, comment: f.comment }))}>Submit rating</Button>
                </div>
              )}
            </Card>
          )}
        </div>

        <Card className="md:col-span-2">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="font-semibold">Chain of custody</h3>
            <Button variant="ghost" onClick={verify}>Verify</Button>
          </div>
          {integrity && <p className="mb-3 rounded bg-slate-100 p-2 text-xs">{integrity}</p>}
          {events.data ? <Timeline events={events.data} /> : <Loading />}
        </Card>
      </div>
    </>
  )
}
