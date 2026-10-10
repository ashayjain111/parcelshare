import { toast } from '../components/Toast'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button, Card, Empty, ListSkeleton, ErrorBox, Field, Loading, PageTitle, Person, StatusBadge } from '../components/ui'
import { api, dateTime, kg, localInput, rupees, toIso } from '../lib'
import type { BookingView, Category, City, ParcelView, TripMatch } from '../lib'

export function NewParcel() {
  const nav = useNavigate()
  const qc = useQueryClient()
  const cities = useQuery({ queryKey: ['cities'], queryFn: () => api<City[]>('/cities'), staleTime: Infinity })
  const cats = useQuery({ queryKey: ['categories'], queryFn: () => api<Category[]>('/parcels/categories'), staleTime: Infinity })
  const [v, setV] = useState({
    origin: '', dest: '', start: localInput(12), end: localInput(96), category: '', weightKg: '1',
    description: '', valueRs: '500', recipientName: '', recipientPhone: '',
  })
  const set = (k: keyof typeof v) => (e: { target: { value: string } }) => setV({ ...v, [k]: e.target.value })
  const selected = cats.data?.find((c) => c.id === Number(v.category))

  const create = useMutation({
    mutationFn: () => api<ParcelView>('/parcels', { body: {
      originCityId: Number(v.origin), destinationCityId: Number(v.dest), windowStart: toIso(v.start), windowEnd: toIso(v.end),
      categoryId: Number(v.category), weightGrams: Math.round(Number(v.weightKg) * 1000), description: v.description,
      declaredValuePaise: Math.round(Number(v.valueRs) * 100), recipientName: v.recipientName, recipientPhone: v.recipientPhone,
    } }),
    onSuccess: (p) => { qc.invalidateQueries(); nav(`/parcels/${p.id}`) },
  })
  const submit = (e: FormEvent) => { e.preventDefault(); create.mutate() }

  const grouped = (tier: Category['tier']) => cats.data?.filter((c) => c.tier === tier) ?? []

  return (
    <div className="mx-auto max-w-lg">
      <PageTitle title="Send a parcel" sub="Small, low-value items only. Travellers will inspect the contents at handover." />
      <Card>
        <form onSubmit={submit} className="stagger space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <Field label="Pickup city"><select required value={v.origin} onChange={set('origin')}><option value="">Select</option>{cities.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
            <Field label="Deliver to"><select required value={v.dest} onChange={set('dest')}><option value="">Select</option>{cities.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Earliest departure"><input type="datetime-local" required value={v.start} onChange={set('start')} /></Field>
            <Field label="Latest departure"><input type="datetime-local" required value={v.end} onChange={set('end')} /></Field>
          </div>
          <Field label="Category" hint={selected && selected.tier !== 'PROHIBITED' ? `Max ${kg(selected.maxGrams)} and ${rupees(selected.maxValuePaise)} declared value.${selected.tier === 'RESTRICTED' ? ' Needs admin review before it goes live.' : ''}` : undefined}>
            <select required value={v.category} onChange={set('category')}>
              <option value="">Select</option>
              <optgroup label="Allowed">{grouped('ALLOWED').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</optgroup>
              <optgroup label="Restricted (admin review)">{grouped('RESTRICTED').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</optgroup>
              <optgroup label="Prohibited (cannot be sent)">{grouped('PROHIBITED').map((c) => <option key={c.id} value={c.id} disabled>{c.name}</option>)}</optgroup>
            </select>
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Weight (kg)"><input type="number" step="any" min="0.05" max="30" required value={v.weightKg} onChange={set('weightKg')} /></Field>
            <Field label="Declared value (₹)"><input type="number" min="0" required value={v.valueRs} onChange={set('valueRs')} /></Field>
          </div>
          <Field label="What is inside?" hint="Be accurate. The traveller will check it matches."><textarea required maxLength={500} rows={2} value={v.description} onChange={set('description')} /></Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Recipient name"><input required value={v.recipientName} onChange={set('recipientName')} /></Field>
            <Field label="Recipient phone"><input value={v.recipientPhone} onChange={set('recipientPhone')} /></Field>
          </div>
          <ErrorBox error={create.error} />
          <Button disabled={create.isPending} className="w-full">Create parcel request</Button>
        </form>
      </Card>
    </div>
  )
}

export function MyParcels() {
  const parcels = useQuery({ queryKey: ['parcels', 'mine'], queryFn: () => api<ParcelView[]>('/parcels/mine') })
  return (
    <>
      <PageTitle title="My parcels" right={<Link to="/parcels/new"><Button>Send a parcel</Button></Link>} />
      {parcels.isLoading ? <ListSkeleton /> : !parcels.data?.length ? <Empty>No parcels yet.</Empty> : (
        <div className="stagger space-y-3">
          {parcels.data.map((p) => (
            <Link key={p.id} to={`/parcels/${p.id}`}>
              <Card className="hover:border-indigo-300">
                <div className="flex items-center justify-between">
                  <p className="font-semibold">{p.publicCode} · {p.origin.name} → {p.destination.name}</p>
                  <StatusBadge status={p.status} />
                </div>
                <p className="text-sm text-slate-600">{p.categoryName} · {kg(p.weightGrams)} · {p.description}</p>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </>
  )
}

export function ParcelDetail() {
  const { id } = useParams()
  const nav = useNavigate()
  const qc = useQueryClient()
  const parcel = useQuery({ queryKey: ['parcel', id], queryFn: () => api<ParcelView>(`/parcels/${id}`) })
  const isOpen = parcel.data?.status === 'OPEN'
  const matches = useQuery({ queryKey: ['parcel-matches', id], enabled: isOpen, queryFn: () => api<TripMatch[]>(`/parcels/${id}/matches`) })
  const bookings = useQuery({ queryKey: ['bookings'], queryFn: () => api<BookingView[]>('/bookings/mine') })
  const book = useMutation({
    mutationFn: (tripId: number) => api<BookingView>('/bookings', { body: { tripId, parcelId: Number(id) } }),
    onSuccess: (b) => { qc.invalidateQueries(); toast('Booking requested. Your payment is held until delivery.'); nav(`/bookings/${b.id}`) },
  })
  const cancel = useMutation({
    mutationFn: () => api(`/parcels/${id}/cancel`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries(),
  })

  if (parcel.isLoading) return <Loading />
  if (parcel.error || !parcel.data) return <ErrorBox error={parcel.error} />
  const p = parcel.data
  const booking = bookings.data?.find((b) => b.parcel.id === p.id && !['CANCELLED', 'REJECTED'].includes(b.status))

  return (
    <>
      <PageTitle title={`${p.publicCode}: ${p.origin.name} → ${p.destination.name}`} sub={`${p.categoryName} · ${kg(p.weightGrams)} · declared ${rupees(p.declaredValuePaise)}`} right={<StatusBadge status={p.status} />} />
      <Card className="mb-4 text-sm">
        <p>{p.description}</p>
        <p className="mt-1 text-slate-500">Departure window: {dateTime(p.windowStart)} – {dateTime(p.windowEnd)} · Recipient: {p.recipientName}</p>
        {p.status === 'PENDING_REVIEW' && <p className="mt-2 rounded bg-amber-50 p-2 text-amber-800">Waiting for admin review ({p.flagReason}). It will go live once approved.</p>}
        {booking && <p className="mt-2"><Link className="text-indigo-600" to={`/bookings/${booking.id}`}>View booking →</Link></p>}
        {(p.status === 'OPEN' || p.status === 'PENDING_REVIEW') && <div className="mt-3"><Button variant="secondary" onClick={() => cancel.mutate()}>Cancel parcel</Button></div>}
        <ErrorBox error={cancel.error} />
      </Card>

      {isOpen && (
        <>
          <h2 className="mb-2 font-semibold">Best travellers for this parcel</h2>
          {matches.isLoading ? <ListSkeleton /> : !matches.data?.length ? (
            <Empty>No travellers on this route yet. Check back soon: your parcel stays visible to travellers.</Empty>
          ) : (
            <div className="stagger space-y-3">
              {matches.data.map((m) => (
                <Card key={m.trip.id}>
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold">{m.trip.origin.name} → {m.trip.destination.name}
                        {m.offered && <span className="ml-2 rounded-full bg-emerald-100 px-2.5 py-0.5 align-middle text-[11px] font-semibold text-emerald-800">✓ Offered to carry</span>}
                      </p>
                      <p className="text-sm text-slate-600">{dateTime(m.trip.departsAt)} · {m.trip.travelMode.toLowerCase()} · {kg(m.trip.capacityGrams - m.trip.reservedGrams)} free</p>
                      <div className="mt-1"><Person u={m.trip.traveller} /></div>
                      <p className="mt-2 text-xs text-slate-500">
                        Why this match ({m.score}/100): {Object.entries(m.breakdown).map(([k, n]) => `${k} ${n}`).join(' · ')}
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="text-xl font-semibold">{rupees(m.quotePaise)}</p>
                      <Button className="mt-2" disabled={book.isPending} onClick={() => book.mutate(m.trip.id)}>Book & pay</Button>
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          )}
          <div className="mt-3"><ErrorBox error={book.error} /></div>
        </>
      )}
    </>
  )
}
