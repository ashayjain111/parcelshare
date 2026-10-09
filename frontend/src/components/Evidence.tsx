import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Button, Card, ErrorBox } from './ui'
import { apiBlob, api, apiUpload, compressImage, dateTime } from '../lib'
import type { EvidenceItem } from '../lib'

const KIND_LABEL: Record<EvidenceItem['kind'], string> = {
  CONTENTS: 'Contents at handover',
  DELIVERY: 'Delivery proof',
  DAMAGE: 'Damage / problem',
}

/** Which photo types the current viewer may add right now (mirrors the server rules; the server still enforces them). */
function allowedKinds(role: string, status: string): EvidenceItem['kind'][] {
  const kinds: EvidenceItem['kind'][] = []
  if (['ACCEPTED', 'HANDED_OVER'].includes(status)) kinds.push('CONTENTS')
  if (role === 'TRAVELLER' && ['IN_TRANSIT', 'DELIVERED', 'DELIVERY_FAILED'].includes(status)) kinds.push('DELIVERY')
  if (['HANDED_OVER', 'IN_TRANSIT', 'DELIVERED', 'DELIVERY_FAILED', 'DISPUTED'].includes(status)) kinds.push('DAMAGE')
  return kinds
}

function Thumb({ bookingId, item }: { bookingId: number; item: EvidenceItem }) {
  const [url, setUrl] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  useEffect(() => {
    let alive = true
    let made: string | null = null
    apiBlob(`/bookings/${bookingId}/evidence/${item.id}/image`)
      .then((b) => {
        if (!alive) return
        made = URL.createObjectURL(b)
        setUrl(made)
      })
      .catch(() => undefined)
    return () => {
      alive = false
      if (made) URL.revokeObjectURL(made)
    }
  }, [bookingId, item.id])

  return (
    <figure className="w-28">
      <button type="button" onClick={() => setOpen(true)} className="block h-24 w-28 overflow-hidden rounded-lg bg-slate-100">
        {url ? <img src={url} alt={KIND_LABEL[item.kind]} className="h-full w-full object-cover" /> : null}
      </button>
      <figcaption className="mt-1 text-[11px] leading-tight text-slate-500">
        {KIND_LABEL[item.kind]}<br />{item.uploaderName} · {dateTime(item.createdAt)}
      </figcaption>
      {open && url && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" onClick={() => setOpen(false)}>
          <img src={url} alt="" className="max-h-full max-w-full rounded-lg" />
        </div>
      )}
    </figure>
  )
}

export default function EvidencePanel({ bookingId, role, status }: { bookingId: number; role: string; status: string }) {
  const qc = useQueryClient()
  const list = useQuery({ queryKey: ['evidence', bookingId], queryFn: () => api<EvidenceItem[]>(`/bookings/${bookingId}/evidence`) })
  const kinds = allowedKinds(role, status)
  const [kind, setKind] = useState<EvidenceItem['kind']>(kinds[0] ?? 'CONTENTS')
  const [verdict, setVerdict] = useState<string | null>(null)
  const input = useRef<HTMLInputElement>(null)

  const upload = useMutation({
    mutationFn: async (file: File) => {
      const blob = await compressImage(file)
      const form = new FormData()
      form.append('kind', kinds.includes(kind) ? kind : kinds[0])
      form.append('file', blob, 'photo.jpg')
      return apiUpload(`/bookings/${bookingId}/evidence`, form)
    },
    onSuccess: () => {
      qc.invalidateQueries()
      if (input.current) input.current.value = ''
    },
  })

  async function verify() {
    const r = await api<{ valid: boolean; photos: number }>(`/bookings/${bookingId}/evidence/verify`)
    setVerdict(r.valid ? `✓ ${r.photos} photo(s) match the hashes recorded in the event history` : '⚠ A photo no longer matches its recorded hash!')
  }

  if (role === 'ADMIN' && !list.data?.length) return null
  return (
    <Card>
      <div className="mb-2 flex items-center justify-between">
        <h3 className="font-semibold">Photo evidence</h3>
        {!!list.data?.length && <Button variant="ghost" onClick={verify}>Verify photos</Button>}
      </div>
      {verdict && <p className="mb-2 rounded bg-slate-100 p-2 text-xs">{verdict}</p>}
      {list.data?.length ? (
        <div className="mb-3 flex flex-wrap gap-3">{list.data.map((e) => <Thumb key={e.id} bookingId={bookingId} item={e} />)}</div>
      ) : (
        <p className="mb-3 text-sm text-slate-500">No photos yet. Photos of the contents at handover protect both of you if there is a dispute.</p>
      )}
      {kinds.length > 0 && role !== 'ADMIN' && (
        <div className="flex flex-wrap items-center gap-2">
          <select className="!w-auto" value={kind} onChange={(e) => setKind(e.target.value as EvidenceItem['kind'])}>
            {kinds.map((k) => <option key={k} value={k}>{KIND_LABEL[k]}</option>)}
          </select>
          <input ref={input} type="file" accept="image/*" capture="environment" className="!w-auto text-xs"
            onChange={(e) => { const f = e.target.files?.[0]; if (f) upload.mutate(f) }} />
          {upload.isPending && <span className="text-xs text-slate-500">Uploading…</span>}
        </div>
      )}
      <div className="mt-2"><ErrorBox error={upload.error} /></div>
      <p className="mt-2 text-xs text-slate-400">Photos are re-encoded on our server, so location and device data in them is removed.</p>
    </Card>
  )
}
