import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'

interface Props {
  children: ReactNode
  /** What to show if a child crashes. Pass `null` to silently drop a decorative part (e.g. the 3D scene). */
  fallback?: ReactNode
}

/**
 * Safety net: a bug in one part must not blank the whole app. Decorative parts (3D) fail silently with `fallback={null}`;
 * the app-level boundary shows a friendly reload message instead of a white screen.
 */
export default class ErrorBoundary extends Component<Props, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('UI crash caught by ErrorBoundary:', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    if (this.props.fallback !== undefined) return this.props.fallback
    return (
      <div className="mx-auto mt-24 max-w-md rounded-2xl border border-slate-900/10 bg-slate-900/[0.04] p-8 text-center">
        <p className="font-display text-2xl font-semibold text-slate-900">Something went wrong</p>
        <p className="mt-2 text-sm text-slate-500">The page hit an unexpected problem. Reloading usually fixes it.</p>
        <button
          onClick={() => window.location.reload()}
          className="mt-6 cursor-pointer rounded-xl bg-gradient-to-r from-indigo-500 to-cyan-400 px-5 py-2.5 text-sm font-semibold text-white"
        >
          Reload
        </button>
      </div>
    )
  }
}
