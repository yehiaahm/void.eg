import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { adminApi, egp, fmtDate } from '../api'
import { useT } from '../i18n'
import { Badge, PageHead, Pager, errText } from '../ui'

const STATUSES = ['ALL', 'NEW', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'RETURNED']

export default function Orders() {
  const { t, lang } = useT()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? 'ALL'
  const page = Number(params.get('page') ?? 0)
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const [q, setQ] = useState(params.get('q') ?? '')
  const [exporting, setExporting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const update = (patch: Record<string, string>) => {
    const next = new URLSearchParams(params)
    Object.entries(patch).forEach(([k, v]) => (v && v !== 'ALL' ? next.set(k, v) : next.delete(k)))
    if (!('page' in patch)) next.delete('page')
    setParams(next, { replace: true })
  }

  // debounce the search box into the URL
  useEffect(() => {
    const id = setTimeout(() => {
      if ((params.get('q') ?? '') !== q.trim()) update({ q: q.trim() })
    }, 300)
    return () => clearTimeout(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q])

  const filters = { status: status === 'ALL' ? undefined : status, q: params.get('q') ?? undefined, from, to }
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'orders', filters, page],
    queryFn: () => adminApi.orders({ ...filters, page }),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  })

  const exportCsv = async () => {
    setExporting(true)
    setError(null)
    try {
      const blob = await adminApi.exportCsv(filters)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `void-orders-${new Date().toISOString().slice(0, 10)}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(errText(e, t))
    } finally {
      setExporting(false)
    }
  }

  return (
    <>
      <PageHead title={t.orders}>
        <button type="button" className="a-btn" onClick={exportCsv} disabled={exporting}>
          {t.exportCsv}
        </button>
      </PageHead>

      <div className="a-filters">
        <input className="a-input a-search" type="search" placeholder={t.searchOrders} value={q} onChange={(e) => setQ(e.target.value)} aria-label={t.search} />
        <label className="a-field" style={{ width: 150 }}>
          <span>{t.from}</span>
          <input className="a-input" type="date" value={from} onChange={(e) => update({ from: e.target.value })} />
        </label>
        <label className="a-field" style={{ width: 150 }}>
          <span>{t.to}</span>
          <input className="a-input" type="date" value={to} onChange={(e) => update({ to: e.target.value })} />
        </label>
      </div>
      <div className="a-chips" style={{ marginBottom: 16 }} role="group" aria-label={t.status}>
        {STATUSES.map((s) => (
          <button key={s} type="button" className="a-chip" aria-pressed={status === s} onClick={() => update({ status: s })}>
            {s === 'ALL' ? t.all : t.statusLabel[s]}
          </button>
        ))}
      </div>
      {error && <p className="a-error" style={{ marginBottom: 12 }}>{error}</p>}

      <div className="a-table-wrap">
        <table className="a-table">
          <thead>
            <tr>
              <th>{t.order}</th>
              <th>{t.customer}</th>
              <th className="a-hide-sm">{t.placed}</th>
              <th>{t.status}</th>
              <th className="a-hide-sm">{t.payment}</th>
              <th className="a-num">{t.total}</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((o) => (
              <tr key={o.number} data-href tabIndex={0} onClick={() => navigate(`/admin/orders/${o.number}`)} onKeyDown={(e) => e.key === 'Enter' && navigate(`/admin/orders/${o.number}`)}>
                <td className="a-mono">{o.number}</td>
                <td>
                  {o.name}
                  <div className="a-faint a-mono" dir="ltr" style={{ textAlign: 'start' }}>
                    {o.phone}
                  </div>
                </td>
                <td className="a-muted a-hide-sm">{fmtDate(o.createdAt, lang, true)}</td>
                <td>
                  <Badge s={o.status}>{t.statusLabel[o.status]}</Badge>
                </td>
                <td className="a-hide-sm">
                  <Badge s={o.paymentStatus}>{t.paymentStatus[o.paymentStatus]}</Badge>
                </td>
                <td className="a-num" dir="ltr">
                  {egp(o.total)}
                  <div className="a-faint" style={{ fontSize: 12 }}>
                    {o.itemCount} {t.pieces}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!isLoading && data?.items.length === 0 && <p className="a-empty">{t.empty}</p>}
        {isLoading && <p className="a-empty">{t.loading}</p>}
      </div>
      {data && <Pager page={data.page} total={data.total} size={data.size} onPage={(p) => update({ page: String(p) })} />}
    </>
  )
}
