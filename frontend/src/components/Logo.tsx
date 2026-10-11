/** Brand mark: an isometric parcel with a glowing edge. */
export default function Logo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none" aria-hidden>
      <defs>
        <linearGradient id="lg1" x1="4" y1="4" x2="28" y2="28" gradientUnits="userSpaceOnUse">
          <stop stopColor="#2563eb" />
          <stop offset="1" stopColor="#38bdf8" />
        </linearGradient>
      </defs>
      <path d="M16 3 28 9.5v13L16 29 4 22.5v-13L16 3Z" stroke="url(#lg1)" strokeWidth="1.8" strokeLinejoin="round" fill="rgb(37 99 235 / 0.10)" />
      <path d="M4 9.5 16 16l12-6.5M16 16v13" stroke="url(#lg1)" strokeWidth="1.8" strokeLinejoin="round" />
      <circle cx="16" cy="16" r="2.2" fill="#38bdf8" />
    </svg>
  )
}
