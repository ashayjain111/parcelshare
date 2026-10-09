import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Card, Loading, PageTitle } from '../components/ui'
import { api, kg, rupees } from '../lib'

interface PublicCategory {
  name: string
  tier: 'ALLOWED' | 'RESTRICTED' | 'PROHIBITED'
  maxGrams: number
  maxValuePaise: number
}

export function ProhibitedItems() {
  const q = useQuery({ queryKey: ['public-categories'], queryFn: () => api<PublicCategory[]>('/public/categories') })
  const by = (t: PublicCategory['tier']) => q.data?.filter((c) => c.tier === t) ?? []
  return (
    <div className="mx-auto max-w-2xl">
      <PageTitle title="What you can and cannot send" sub="ParcelShare never inspects parcels, so these rules protect everyone, especially travellers." />
      {q.isLoading ? <Loading /> : (
        <div className="space-y-4">
          <Card className="border-emerald-200">
            <h2 className="font-semibold text-emerald-800">Allowed</h2>
            <ul className="mt-2 space-y-1 text-sm">
              {by('ALLOWED').map((c) => <li key={c.name}>{c.name} <span className="text-slate-500">· up to {kg(c.maxGrams)} and {rupees(c.maxValuePaise)}</span></li>)}
            </ul>
          </Card>
          <Card className="border-amber-200">
            <h2 className="font-semibold text-amber-800">Allowed after admin review</h2>
            <ul className="mt-2 space-y-1 text-sm">
              {by('RESTRICTED').map((c) => <li key={c.name}>{c.name} <span className="text-slate-500">· up to {kg(c.maxGrams)} and {rupees(c.maxValuePaise)}</span></li>)}
            </ul>
          </Card>
          <Card className="border-red-200">
            <h2 className="font-semibold text-red-800">Never allowed</h2>
            <ul className="mt-2 list-inside list-disc space-y-1 text-sm">
              {by('PROHIBITED').map((c) => <li key={c.name}>{c.name}</li>)}
            </ul>
            <p className="mt-3 text-xs text-slate-500">Travellers must open and look at every parcel before accepting it, and may refuse any parcel for any reason.</p>
          </Card>
        </div>
      )}
    </div>
  )
}

export function Terms() {
  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <PageTitle title="Terms of use" sub="Plain-language draft. Have it reviewed by a lawyer or your institution's legal cell before a public launch." />
      <Card className="space-y-4 text-sm leading-relaxed text-slate-700">
        <section>
          <h2 className="font-semibold text-slate-900">1. What ParcelShare is</h2>
          <p>ParcelShare is an online marketplace that connects people who want a small parcel moved between cities with travellers who are already making that journey. We are not a courier or carrier. We never collect, store, inspect or transport parcels. The sender and the traveller make their own arrangement; we provide matching, payment handling, a record of events and a dispute process.</p>
        </section>
        <section>
          <h2 className="font-semibold text-slate-900">2. No insurance</h2>
          <p>Goods are not insured. Keep declared values low and send only items you could bear to lose. Our dispute process can release or refund the delivery fee held for a booking, but it does not compensate for the value of the goods.</p>
        </section>
        <section>
          <h2 className="font-semibold text-slate-900">3. What may be sent</h2>
          <p>You must follow the <Link className="text-indigo-600" to="/prohibited">prohibited items</Link> list. Senders must describe the contents truthfully. Sending prohibited, illegal or dangerous items is grounds for immediate suspension and may be reported to the authorities.</p>
        </section>
        <section>
          <h2 className="font-semibold text-slate-900">4. Traveller responsibilities</h2>
          <p>Travellers must look inside every parcel at handover and confirm it matches the description. Never carry anything you have not inspected, and refuse anything that seems wrong. Travelling by air is not supported. Travellers are responsible for following the rules of their transport operator.</p>
        </section>
        <section>
          <h2 className="font-semibold text-slate-900">5. Handover, delivery and evidence</h2>
          <p>Handover is confirmed with a one-time code. Delivery is confirmed with the recipient's code. Photos you upload are re-encoded to remove hidden data and are kept with the booking as evidence. Every event is time-stamped and chained so that history cannot be silently changed.</p>
        </section>
        <section>
          <h2 className="font-semibold text-slate-900">6. Payments and fees</h2>
          <p>The sender pays when booking. The money is held and released to the traveller after delivery, once a dispute window (currently 48 hours) passes or the sender confirms receipt. We keep a commission (currently 10%). If a sender cancels after the traveller has accepted, a cancellation fee (currently 10%) goes to the traveller. If a traveller cancels, the sender is refunded in full. In the current demo version, payments are simulated.</p>
        </section>
        <section>
          <h2 className="font-semibold text-slate-900">7. Disputes</h2>
          <p>Either party, or the recipient, can raise a dispute while the money is still held. The money is frozen, and an admin reviews the booking's history and photos and decides to release, refund or split the payment. The admin's decision on the delivery fee is final within ParcelShare.</p>
        </section>
        <section>
          <h2 className="font-semibold text-slate-900">8. Your data</h2>
          <p>We store your name, email, phone number, bookings, ratings and photos you upload. Your phone number is shown to the other party only after a booking is accepted. ID details used to verify travellers are checked by an admin and are not stored by the app. You can ask us to delete your account and personal data, subject to records we must keep for disputes.</p>
        </section>
        <section>
          <h2 className="font-semibold text-slate-900">9. Suspension</h2>
          <p>We may suspend accounts that break these terms, misuse disputes, or put others at risk.</p>
        </section>
      </Card>
    </div>
  )
}
