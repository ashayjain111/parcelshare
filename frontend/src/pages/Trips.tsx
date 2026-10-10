import { toast } from '../components/Toast'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import TiltCard from '../components/TiltCard'
import { Button, Card, Empty, ListSkeleton, ErrorBox, Field, Loading, PageTitle, Person, StatusBadge } from '../components/ui'
import { api, dateTime, kg, localInput, rupees, toIso } from '../lib'
import type { BookingView, City, ParcelMatch, ParcelView, TripView } from '../lib'
import { useAuth } from '../auth'

function useCities() {
  return useQuery({ queryKey: ['cities'], queryFn: () => api<City[]>('/cities'), staleTime: Infinity })
}

// ---------------------------------------------------------------- Browse (sender side)

export function BrowseTrips() {
  const { data: cities } = useCities()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [date, setDate] = useState('')
  const qs = new URLSearchParams()
  if (from) qs.set('from', from)
  if (to) qs.set('to', to)
  if (date) qs.set('date', date)
  const trips = useQuery({ queryKey: ['trips', from, to, date], queryFn: () => api<TripView[]>(`/trips?${qs}`) })

  return (
    <>
      <PageTitle title="Find a traveller" sub="Upcoming trips with spare capacity." />
      <Card className="mb-4 grid gap-3 sm:grid-cols-3">
        <Field label="From">
          <select value={from} onChange={(e) => setFrom(e.target.value)}>
            <option value="">Anywhere</option>
            {cities?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </Field>
        <Field label="To">
          <select value={to} onChange={(e) => setTo(e.target.value)}>
            <option value="">Anywhere</option>
            {cities?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </Field>
        <Field label="Date"><input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></Field>
      </Card>
      {trips.isLoading ? <ListSkeleton /> : trips.error ? <ErrorBox error={trips.error} /> : !trips.data?.length ? (
        <Empty>No trips match yet. <Link to="/parcels/new" className="text-indigo-600">Post your parcel</Link> and travellers on that route will see it.</Empty>
      ) : (
        <div className="stagger space-y-3">{trips.data.map((t) => <TripCard key={t.id} trip={t} />)}</div>
      )}
    </>
  )
}

function TripCard({ trip }: { trip: TripView }) {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  const mine = user?.id === trip.traveller.id
  return (
    <TiltCard>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-lg font-semibold">{trip.origin.name} → {trip.destination.name}</p>
          <p className="text-sm text-slate-600">
            {dateTime(trip.departsAt)} · {trip.travelMode.toLowerCase()} · {kg(trip.capacityGrams - trip.reservedGrams)} free · {rupees(trip.pricePerKgPaise)}/kg
          </p>
          <div className="mt-1"><Person u={trip.traveller} /></div>
        </div>
        {mine ? <span className="text-xs text-slate-500">Your trip</span> : <Button variant="secondary" onClick={() => setOpen(!open)}>{open ? 'Close' : 'Book with this traveller'}</Button>}
      </div>
      {open && <BookPanel trip={trip} />}
    </TiltCard>
  )
}

function BookPanel({ trip }: { trip: TripView }) {
  const nav = useNavigate()
  const qc = useQueryClient()
  const parcels = useQuery({ queryKey: ['parcels', 'mine'], queryFn: () => api<ParcelView[]>('/parcels/mine') })
  const book = useMutation({
    mutationFn: (parcelId: number) => api<BookingView>('/bookings', { body: { tripId: trip.id, parcelId } }),
    onSuccess: (b) => {
      qc.invalidateQueries()
      toast('Booking requested. Your payment is held until delivery.')
      nav(`/bookings/${b.id}`)
    },
  })
  const open = parcels.data?.filter((p) => p.status === 'OPEN') ?? []
  return (
    <div className="mt-3 space-y-2 border-t border-slate-100 pt-3">
      {parcels.isLoading ? <ListSkeleton /> : !open.length ? (
        <p className="text-sm text-slate-600">You have no open parcels. <Link to="/parcels/new" className="text-indigo-600">Create one first</Link>.</p>
      ) : open.map((p) => (
        <div key={p.id} className="flex items-center justify-between gap-2 rounded-lg bg-slate-50 px-3 py-2 text-sm">
          <span>{p.publicCode} · {p.origin.name} → {p.destination.name} · {kg(p.weightGrams)} · {p.categoryName}</span>
          <Button disabled={book.isPending} onClick={() => book.mutate(p.id)}>Book & pay</Button>
        </div>
      ))}
      <ErrorBox error={book.error} />
      <p className="text-xs text-slate-500">Payment is held securely and only released to the traveller after delivery.</p>
    </div>
  )
}

// ---------------------------------------------------------------- Post a trip (traveller side)

export function NewTrip() {
  const { user } = useAuth()
  const nav = useNavigate()
  const qc = useQueryClient()
  const { data: cities } = useCities()
  const [v, setV] = useState({ origin: '', dest: '', departs: localInput(48), mode: 'BUS', capKg: '5', priceRs: '100' })
  const set = (k: keyof typeof v) => (e: { target: { value: string } }) => setV({ ...v, [k]: e.target.value })
  const create = useMutation({
    mutationFn: () => api<TripView>('/trips', { body: {
      originCityId: Number(v.origin), destinationCityId: Number(v.dest), departsAt: toIso(v.departs),
      travelMode: v.mode, capacityGrams: Math.round(Number(v.capKg) * 1000), pricePerKgPaise: Math.round(Number(v.priceRs) * 100),
    } }),
    onSuccess: (t) => { qc.invalidateQueries(); nav(`/trips/${t.id}`) },
  })
  const submit = (e: FormEvent) => { e.preventDefault(); create.mutate() }

  return (
    <div className="mx-auto max-w-lg">
      <PageTitle title="Post a trip" sub="Earn by carrying small parcels on a journey you are already making." />
      {user?.travellerVerification !== 'APPROVED' && (
        <Card className="mb-4 border-amber-200 bg-amber-50 text-sm text-amber-900">
          Only verified travellers can post trips. <Link to="/profile" className="font-medium underline">Request verification</Link> from your profile
          (status: {user?.travellerVerification}).
        </Card>
      )}
      <Card>
        <form onSubmit={submit} className="stagger space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <Field label="From"><select required value={v.origin} onChange={set('origin')}><option value="">Select</option>{cities?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
            <Field label="To"><select required value={v.dest} onChange={set('dest')}><option value="">Select</option>{cities?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
          </div>
          <Field label="Departure" hint="Local time"><input type="datetime-local" required value={v.departs} onChange={set('departs')} /></Field>
          <div className="grid grid-cols-3 gap-3">
            <Field label="Travelling by" hint="No flights">
              <select value={v.mode} onChange={set('mode')}><option value="BUS">Bus</option><option value="TRAIN">Train</option><option value="CAR">Car</option></select>
            </Field>
            <Field label="Spare capacity (kg)"><input type="number" step="0.5" min="0.5" max="30" required value={v.capKg} onChange={set('capKg')} /></Field>
            <Field label="Price per kg (₹)"><input type="number" min="10" max="2000" required value={v.priceRs} onChange={set('priceRs')} /></Field>
          </div>
          <ErrorBox error={create.error} />
          <Button disabled={create.isPending} className="w-full">Post trip</Button>
        </form>
      </Card>
    </div>
  )
}

// ---------------------------------------------------------------- My trips & trip detail

export function MyTrips() {
  const trips = useQuery({ queryKey: ['trips', 'mine'], queryFn: () => api<TripView[]>('/trips/mine') })
  return (
    <>
      <PageTitle title="My trips" right={<Link to="/trips/new"><Button>Post a trip</Button></Link>} />
      {trips.isLoading ? <ListSkeleton /> : !trips.data?.length ? <Empty>You have not posted any trips yet.</Empty> : (
        <div className="stagger space-y-3">
          {trips.data.map((t) => (
            <Link key={t.id} to={`/trips/${t.id}`}>
              <Card className="hover:border-indigo-300">
                <div className="flex items-center justify-between">
                  <p className="font-semibold">{t.origin.name} → {t.destination.name}</p>
                  <StatusBadge status={t.status} />
                </div>
                <p className="text-sm text-slate-600">{dateTime(t.departsAt)} · {kg(t.reservedGrams)} of {kg(t.capacityGrams)} booked</p>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </>
  )
}

export function TripDetail() {
  const { id } = useParams()
  const trip = useQuery({ queryKey: ['trip', id], queryFn: () => api<TripView>(`/trips/${id}`) })
  const { user } = useAuth()
  const mine = trip.data && user?.id === trip.data.traveller.id
  const matches = useQuery({
    queryKey: ['trip-matches', id], enabled: !!mine,
    queryFn: () => api<ParcelMatch[]>(`/trips/${id}/matches`),
  })
  const qc = useQueryClient()
  const offer = useMutation({
    mutationFn: (parcelId: number) => api(`/trips/${id}/offers`, { body: { parcelId } }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['trip-matches', id] }); toast('Offer sent. The sender has been notified.') },
  })
  const cancel = useMutation({
    mutationFn: () => api(`/trips/${id}/cancel`, { method: 'POST' }),
    onSuccess: () => { qc.invalidateQueries(); toast('Trip cancelled. Any requests were declined and refunded.') },
  })
  if (trip.isLoading) return <Loading />
  if (trip.error || !trip.data) return <ErrorBox error={trip.error} />
  const t = trip.data
  const pct = Math.round((t.reservedGrams / t.capacityGrams) * 100)
  return (
    <>
      <PageTitle title={`${t.origin.name} → ${t.destination.name}`} sub={`${dateTime(t.departsAt)} · ${t.travelMode.toLowerCase()}`} right={
        <div className="flex items-center gap-3">
          {mine && t.status === 'OPEN' && (
            <Button variant="danger" disabled={cancel.isPending} onClick={() => { if (confirm('Cancel this trip? Pending requests are declined and refunded, and accepted bookings count against your reliability.')) cancel.mutate() }}>Cancel trip</Button>
          )}
          <StatusBadge status={t.status} />
        </div>
      } />
      <div className="mb-3"><ErrorBox error={cancel.error} /></div>
      <Card className="mb-4">
        <p className="text-sm text-slate-600">Capacity: {kg(t.reservedGrams)} booked of {kg(t.capacityGrams)} · {rupees(t.pricePerKgPaise)}/kg</p>
        <div className="mt-2 h-2 rounded-full bg-slate-100"><div className="h-2 rounded-full bg-indigo-500" style={{ width: `${pct}%` }} /></div>
        <div className="mt-2"><Person u={t.traveller} /></div>
      </Card>
      {mine && (
        <>
          <h2 className="mb-2 font-semibold">Parcels that fit this trip</h2>
          <p className="mb-3 text-sm text-slate-500">Offer to carry a parcel and the sender is notified. They choose, book and pay, and you then accept from the Bookings page.</p>
          <ErrorBox error={offer.error} />
          {matches.isLoading ? <ListSkeleton /> : !matches.data?.length ? <Empty>No matching parcels yet. They will appear here when senders post them.</Empty> : (
            <div className="stagger space-y-2">
              {matches.data.map((m) => (
                <Card key={m.parcel.id}>
                  <div className="flex flex-wrap justify-between gap-2">
                    <div>
                      <p className="font-medium">{m.parcel.publicCode} · {m.parcel.categoryName} · {kg(m.parcel.weightGrams)}</p>
                      <p className="text-sm text-slate-600">{m.parcel.origin.name} → {m.parcel.destination.name} · declared {rupees(m.parcel.declaredValuePaise)}</p>
                      <p className="text-sm text-slate-500">{m.parcel.description}</p>
                      <div className="mt-1"><Person u={m.parcel.sender} role="sender" /></div>
                    </div>
                    <div className="text-right">
                      <p className="font-display text-xl font-semibold text-emerald-700">{rupees(m.earningsPaise)}</p>
                      <p className="text-xs text-slate-500">your earnings · match {m.score}/100</p>
                      {m.offered
                        ? <span className="mt-2 inline-block rounded-full bg-emerald-100 px-3 py-1 text-xs font-semibold text-emerald-800">✓ Offer sent</span>
                        : <Button className="mt-2" variant="secondary" disabled={offer.isPending} onClick={() => offer.mutate(m.parcel.id)}>Offer to carry</Button>}
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </>
      )}
    </>
  )
}
