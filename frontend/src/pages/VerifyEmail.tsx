import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth'
import { Card, ErrorBox, Loading } from '../components/ui'
import { api } from '../lib'

export default function VerifyEmail() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const { refresh } = useAuth()
  const q = useQuery({ queryKey: ['verify', token], enabled: !!token, retry: false, queryFn: () => api(`/auth/verify?token=${encodeURIComponent(token)}`) })
  useEffect(() => {
    if (q.isSuccess) refresh()
  }, [q.isSuccess, refresh])

  return (
    <Card className="mx-auto max-w-sm text-center">
      <h1 className="mb-2 text-xl font-semibold">Email verification</h1>
      {!token ? <p className="text-sm text-slate-600">This link is missing its token.</p>
        : q.isLoading ? <Loading />
        : q.isSuccess ? <><p className="text-sm text-emerald-700">✓ Your email is verified.</p><p className="mt-3"><Link className="text-indigo-600" to="/trips">Continue to ParcelShare</Link></p></>
        : <><ErrorBox error={q.error} /><p className="mt-2 text-xs text-slate-500">The link may already have been used.</p></>}
    </Card>
  )
}
