import jsQR from 'jsqr'
import { useEffect, useRef, useState } from 'react'
import { Button } from './ui'

/** Opens the camera and calls onResult once with the first QR code text it reads. Works over HTTPS or localhost. */
export default function QrScanner({ onResult, onClose }: { onResult: (text: string) => void; onClose: () => void }) {
  const video = useRef<HTMLVideoElement>(null)
  const callback = useRef(onResult)
  useEffect(() => {
    callback.current = onResult
  })
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let stream: MediaStream | null = null
    let raf = 0
    let stopped = false
    const canvas = document.createElement('canvas')

    async function start() {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
        const v = video.current
        if (!v || stopped) return
        v.srcObject = stream
        await v.play()
        const tick = () => {
          if (stopped) return
          if (v.readyState === v.HAVE_ENOUGH_DATA && v.videoWidth > 0) {
            canvas.width = v.videoWidth
            canvas.height = v.videoHeight
            const ctx = canvas.getContext('2d', { willReadFrequently: true })
            if (ctx) {
              ctx.drawImage(v, 0, 0)
              const img = ctx.getImageData(0, 0, canvas.width, canvas.height)
              const code = jsQR(img.data, img.width, img.height, { inversionAttempts: 'dontInvert' })
              if (code?.data) {
                stopped = true
                callback.current(code.data)
                return
              }
            }
          }
          raf = requestAnimationFrame(tick)
        }
        raf = requestAnimationFrame(tick)
      } catch {
        setError('Could not open the camera. Allow camera access, or type the code instead.')
      }
    }
    start()
    return () => {
      stopped = true
      cancelAnimationFrame(raf)
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [])

  return (
    <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
      {error ? <p className="text-sm text-red-700">{error}</p> : <video ref={video} playsInline muted className="w-full rounded-lg bg-black" />}
      <div className="flex items-center justify-between">
        <p className="text-xs text-slate-500">Point the camera at the sender's QR code.</p>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
      </div>
    </div>
  )
}
