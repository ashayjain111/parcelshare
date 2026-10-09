import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { Button, Card, ErrorBox, Field, Loading, StatusBadge } from '../components/ui'
import { EVENT_LABELS, api, dateTime } from '../lib'

interface RecipientView {
  parcelCode: string
  recipientName: string
  route: string
  travellerName: string
  status: string
  deliveryCode: string | null
  canReportProblem: boolean
  timeline: { type: string; at: string }[]
}

/** Public page for the recipient (no account). The link itself is their credential. */
export default function Receive() {
  const { token } = useParams()
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['receive', token], queryFn: () => api<RecipientView>(`/public/receive/${token}`), refetchInterval: 15000 })
  const [reason, setReason] = useState('')
  const [open, setOpen] = useState(false)
  const report = useMutation({
    mutationFn: () => api(`/public/receive/${token}/report`, { body: { reason } }),
    onSuccess: () => { setOpen(false); qc.invalidateQueries() },
  })

  if (q.isLoading) return <Loading />
  if (q.error || !q.data) return <ErrorBox error={q.error} />
  const r = q.data
  return (
    <div className="mx-auto max-w-md space-y-4">
      <Card>
        <p className="text-xs text-slate-500">Parcel for {r.recipientName}</p>
        <h1 className="text-xl font-semibold">{r.parcelCode} · {r.route}</h1>
        <p className="mt-1 text-sm text-slate-600">Carried by {r.travellerName}</p>
        <div className="mt-2"><StatusBadge status={r.status} /></div>
      </Card>

      {r.deliveryCode && (
        <Card className="border-indigo-200 bg-indigo-50 text-center">
          <p className="text-sm font-semibold text-indigo-900">Your delivery code</p>
          <p className="my-2 font-mono text-4xl tracking-widest text-indigo-900">{r.deliveryCode}</p>
          <p className="text-xs text-indigo-800">Only tell this code to the traveller once you are holding your parcel and are happy with it.</p>
        </Card>
      )}

      {r.canReportProblem && (
        <Card>
          <p className="text-sm text-slate-700">Delivered. Something wrong with the parcel? Tell us before the payment is released.</p>
          {!open ? <Button className="mt-2" variant="secondary" onClick={() => setOpen(true)}>Report a problem</Button> : (
            <div className="mt-3 space-y-2">
              <Field label="What is wrong?"><textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
              <ErrorBox error={report.error} />
              <Button variant="danger" disabled={!reason.trim() || report.isPending} onClick={() => report.mutate()}>Submit report</Button>
            </div>
          )}
        </Card>
      )}

      <Card>
        <h2 className="mb-2 font-semibold">Progress</h2>
        <ul className="space-y-1 text-sm">
          {r.timeline.map((t, i) => <li key={i}><span className="text-slate-500">{dateTime(t.at)}</span> · {EVENT_LABELS[t.type] ?? t.type}</li>)}
        </ul>
      </Card>
    </div>
  )
}
