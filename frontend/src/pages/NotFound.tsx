import { Link } from 'react-router-dom'
import Logo from '../components/Logo'

export default function NotFound() {
  return (
    <div className="mx-auto flex max-w-lg flex-col items-center px-4 pb-24 pt-20 text-center">
      <div className="animate-float"><Logo size={64} /></div>
      <p className="mt-8 font-display text-8xl font-semibold tracking-tight text-gradient">404</p>
      <h1 className="mt-2 font-display text-2xl font-semibold text-slate-900">This parcel took a wrong turn</h1>
      <p className="mt-3 text-sm text-slate-500">The page you are looking for does not exist, or it moved.</p>
      <Link to="/" className="mt-8 rounded-xl bg-gradient-to-r from-indigo-500 to-cyan-400 px-6 py-3 text-sm font-semibold text-white shadow-[0_8px_30px_-8px_rgba(37,99,235,0.9)] transition hover:brightness-110">
        Back to home
      </Link>
    </div>
  )
}
