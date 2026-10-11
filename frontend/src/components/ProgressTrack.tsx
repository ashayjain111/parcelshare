const STEPS = [
  { key: 'REQUESTED', label: 'Requested' },
  { key: 'ACCEPTED', label: 'Accepted' },
  { key: 'HANDED_OVER', label: 'Handed over' },
  { key: 'IN_TRANSIT', label: 'In transit' },
  { key: 'DELIVERED', label: 'Delivered' },
]

const OFF_TRACK = new Set(['CANCELLED', 'REJECTED', 'DISPUTED', 'RESOLVED', 'DELIVERY_FAILED'])

/** Glowing five-step journey line for a booking. Off-track states (dispute, cancelled...) show a clear banner instead. */
export default function ProgressTrack({ status }: { status: string }) {
  const idx = STEPS.findIndex((s) => s.key === status)
  const done = status === 'RESOLVED' ? 4 : idx
  const pct = Math.max(0, Math.min(100, (Math.max(done, 0) / (STEPS.length - 1)) * 100))
  const off = OFF_TRACK.has(status)

  return (
    <div className="mb-6">
      <div className="relative mx-3 mt-2">
        <div className="absolute left-0 right-0 top-[7px] h-[2px] rounded-full bg-white/10" />
        <div
          className="absolute left-0 top-[7px] h-[2px] rounded-full bg-gradient-to-r from-indigo-500 to-cyan-400 shadow-[0_0_14px_2px_rgba(241,212,154,0.55)] transition-[width] duration-1000 ease-out"
          style={{ width: `${pct}%` }}
        />
        <ol className="relative flex justify-between">
          {STEPS.map((s, i) => {
            const reached = i <= done
            const current = i === idx
            return (
              <li key={s.key} className="flex w-0 flex-col items-center">
                <span
                  className={`h-4 w-4 rounded-full border-2 transition-all duration-700 ${
                    reached
                      ? 'border-cyan-300 bg-cyan-400 shadow-[0_0_16px_3px_rgba(241,212,154,0.65)]'
                      : 'border-white/20 bg-[#14110d]'
                  } ${current ? 'scale-125' : ''}`}
                />
                <span className={`mt-2 whitespace-nowrap text-[11px] font-medium ${reached ? 'text-slate-800' : 'text-slate-400'}`}>
                  {s.label}
                </span>
              </li>
            )
          })}
        </ol>
      </div>
      {off && (
        <p className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-center text-xs font-medium text-amber-800">
          {status === 'DISPUTED' && 'Dispute open: the payment is frozen while an admin reviews the evidence.'}
          {status === 'RESOLVED' && 'Dispute resolved by an admin.'}
          {status === 'CANCELLED' && 'This booking was cancelled.'}
          {status === 'REJECTED' && 'The traveller declined this request.'}
          {status === 'DELIVERY_FAILED' && 'Delivery attempt failed. Retry or raise a dispute.'}
        </p>
      )}
    </div>
  )
}
