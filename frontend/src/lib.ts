// ---------------------------------------------------------------- API client

const BASE: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8090'
const TOKEN_KEY = 'ps_token'

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export const getToken = (): string | null => {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export const setToken = (t: string | null) => {
  try {
    if (t) localStorage.setItem(TOKEN_KEY, t)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* storage unavailable: session just will not persist */
  }
}

/** fetch that turns a network failure into a clear, human message (offline, server asleep, wrong URL). */
async function safeFetch(url: string, init?: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init)
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection, or it may be waking up. Try again in a moment.')
  }
}

export async function api<T = unknown>(path: string, opts: { method?: string; body?: unknown } = {}): Promise<T> {
  const token = getToken()
  const res = await safeFetch(BASE + path, {
    method: opts.method ?? (opts.body !== undefined ? 'POST' : 'GET'),
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  })
  if (res.status === 401 && token) {
    setToken(null)
    window.dispatchEvent(new Event('ps-logout'))
  }
  const text = await res.text()
  let data: unknown = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }
  if (!res.ok) {
    const msg = (data as { error?: string } | null)?.error ?? res.statusText ?? 'Request failed'
    throw new ApiError(res.status, msg)
  }
  return data as T
}

export const fetchConfig = () => api<{ demoMode: boolean }>('/public/config')

/** Authenticated binary download (images can't use a plain <img src> because the API needs the bearer token). */
export async function apiBlob(path: string): Promise<Blob> {
  const token = getToken()
  const res = await safeFetch(BASE + path, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (!res.ok) throw new ApiError(res.status, 'Could not load file')
  return res.blob()
}

export async function apiUpload<T = unknown>(path: string, form: FormData): Promise<T> {
  const token = getToken()
  const res = await safeFetch(BASE + path, { method: 'POST', headers: token ? { Authorization: `Bearer ${token}` } : {}, body: form })
  const text = await res.text()
  let data: unknown = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }
  if (!res.ok) throw new ApiError(res.status, (data as { error?: string } | null)?.error ?? 'Upload failed')
  return data as T
}

/** Shrinks a photo in the browser before upload (max 1600px, JPEG) so phone photos upload fast on slow networks. */
export async function compressImage(file: File): Promise<Blob> {
  const bmp = await createImageBitmap(file)
  const scale = Math.min(1, 1600 / Math.max(bmp.width, bmp.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(bmp.width * scale))
  canvas.height = Math.max(1, Math.round(bmp.height * scale))
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Your browser cannot process images')
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(bmp, 0, 0, canvas.width, canvas.height)
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Could not process the image'))), 'image/jpeg', 0.82),
  )
}

export interface EvidenceItem {
  id: number
  kind: 'CONTENTS' | 'DELIVERY' | 'DAMAGE'
  uploaderId: number
  uploaderName: string
  uploaderRole: 'SENDER' | 'TRAVELLER'
  sizeBytes: number
  sha256: string
  createdAt: string
}

export interface PolicyCategory {
  id: number
  name: string
  tier: 'ALLOWED' | 'RESTRICTED' | 'PROHIBITED'
  maxGrams: number
  maxValuePaise: number
  active: boolean
}

// ---------------------------------------------------------------- Types (mirror the backend views)

export interface User {
  id: number
  email: string
  phone: string | null
  fullName: string
  role: 'USER' | 'ADMIN'
  emailVerified: boolean
  travellerVerification: 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED'
  ratingAvg: number
  ratingCount: number
  completedDeliveries: number
  reliabilityPercent: number
  identityVerified: boolean
  identityProvider: string | null
}

export interface UserSummary {
  id: number
  name: string
  ratingAvg: number
  ratingCount: number
  completedDeliveries: number
  reliabilityPercent: number
  verifiedTraveller: boolean
}

export interface City {
  id: number
  name: string
  state: string
  regionId: number
  regionName: string
}

export interface Category {
  id: number
  name: string
  tier: 'ALLOWED' | 'RESTRICTED' | 'PROHIBITED'
  maxGrams: number
  maxValuePaise: number
}

export interface TripView {
  id: number
  traveller: UserSummary
  origin: City
  destination: City
  departsAt: string
  travelMode: string
  capacityGrams: number
  reservedGrams: number
  pricePerKgPaise: number
  status: string
}

export interface ParcelView {
  id: number
  publicCode: string
  sender: UserSummary
  origin: City
  destination: City
  windowStart: string
  windowEnd: string
  categoryName: string
  categoryTier: string
  weightGrams: number
  description: string
  declaredValuePaise: number
  recipientName: string | null
  status: string
  flagReason: string | null
  createdAt: string
}

export interface TripMatch {
  trip: TripView
  score: number
  breakdown: Record<string, number>
  quotePaise: number
  offered: boolean
}

export interface ParcelMatch {
  parcel: ParcelView
  score: number
  breakdown: Record<string, number>
  earningsPaise: number
  offered: boolean
}

export interface BookingView {
  id: number
  parcel: ParcelView
  trip: TripView
  sender: UserSummary
  traveller: UserSummary
  status: string
  pricePaise: number
  commissionPaise: number
  travellerPayoutPaise: number
  paymentStatus: string
  releaseAt: string | null
  viewerRole: 'SENDER' | 'TRAVELLER' | 'ADMIN'
  actions: string[]
  deliveryCode: string | null
  recipientLink: string | null
  counterpartPhone: string | null
  handoverExpiresAt: string | null
  createdAt: string
}

export interface EventView {
  id: number
  type: string
  actorId: number | null
  actorName: string
  payload: string
  createdAt: string
  hash: string
}

export interface DisputeView {
  id: number
  bookingId: number
  parcelCode: string
  senderName: string
  travellerName: string
  bookingStatus: string
  amountPaise: number
  raisedByRole: string
  reason: string
  status: string
  resolution: string | null
  travellerSharePaise: number | null
  adminNote: string | null
  createdAt: string
  resolvedAt: string | null
}

export interface AdminUser {
  id: number
  email: string
  phone: string | null
  fullName: string
  role: string
  status: string
  emailVerified: boolean
  travellerVerification: string
  verificationNote: string | null
  ratingAvg: number
  ratingCount: number
  completedDeliveries: number
  cancelledCount: number
  disputedCount: number
}

// ---------------------------------------------------------------- Formatting

export const rupees = (paise: number): string =>
  '₹' + (paise / 100).toLocaleString('en-IN', { maximumFractionDigits: 2 })

export const kg = (grams: number): string => `${(grams / 1000).toLocaleString('en-IN', { maximumFractionDigits: 2 })} kg`

export const dateTime = (iso: string): string =>
  new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

export const dateOnly = (iso: string): string =>
  new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })

/** <input type="datetime-local"> value for "now + hours" in the user's local zone. */
export const localInput = (hoursFromNow: number): string => {
  const d = new Date(Date.now() + hoursFromNow * 3600_000)
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset())
  return d.toISOString().slice(0, 16)
}

export const toIso = (localValue: string): string => new Date(localValue).toISOString()

export const EVENT_LABELS: Record<string, string> = {
  BOOKING_REQUESTED: 'Booking requested, payment held',
  BOOKING_ACCEPTED: 'Traveller accepted the booking',
  BOOKING_REJECTED: 'Traveller declined the request',
  CANCELLED_BY_SENDER: 'Cancelled by sender',
  CANCELLED_BY_TRAVELLER: 'Cancelled by traveller',
  REQUEST_EXPIRED: 'Request expired (no response)',
  HANDOVER_CODE_ISSUED: 'Handover code generated',
  HANDOVER_CONFIRMED: 'Handover confirmed: traveller has the parcel',
  JOURNEY_STARTED: 'Journey started, parcel in transit',
  ARRIVED: 'Traveller reported arrival',
  DELIVERY_CONFIRMED: 'Delivery confirmed with recipient code',
  DELIVERY_FAILED: 'Delivery failed (recipient unavailable)',
  DELIVERY_RETRY: 'Delivery retry started',
  PAYMENT_RELEASED: 'Payment released to traveller',
  DISPUTE_RAISED: 'Dispute raised, payment frozen',
  DISPUTE_RESOLVED: 'Dispute resolved by admin',
  RATED: 'Rating submitted',
  EVIDENCE_ADDED: 'Photo evidence added',
}

export interface NotificationItem {
  id: number
  type: string
  title: string
  body: string | null
  link: string | null
  read: boolean
  createdAt: string
}

export interface Inbox {
  unread: number
  items: NotificationItem[]
}

export interface Review {
  score: number
  comment: string | null
  fromName: string
  createdAt: string
}

/** "5 min ago" style label for notifications. */
export const timeAgo = (iso: string): string => {
  const s = Math.max(1, Math.round((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 60) return 'just now'
  if (s < 3600) return `${Math.floor(s / 60)} min ago`
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`
  return `${Math.floor(s / 86400)} d ago`
}
