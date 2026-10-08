import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { adminApi, compact, egp, fmtDate, type Dashboard as D, type Kpis } from '../api'
import { useT } from '../i18n'
import { StoreSwitch } from '../StoreSwitch'
import { Badge, PageHead } from '../ui'

const RANGES = [7, 30, 90]

function Delta({ cur, prev }: { cur: number; prev: number }) {
  const { t } = useT()
  if (!prev) return <span className="a-delta"><small>{t.vsPrev}</small></span>
  const pct = Math.round(((cur - prev) / prev) * 100)
  const dir = pct > 0 ? 'up' : pct < 0 ? 'down' : 'flat'
  return (
    <span className="a-delta" data-dir={dir}>
      <span aria-hidden="true">{dir === 'up' ? '▲' : dir === 'down' ? '▼' : '–'}</span>
      {pct > 0 ? '+' : ''}
      {pct}%<small>{t.vsPrev}</small>
    </span>
  )
}

function Kpi({ label, value, cur, prev }: { label: string; value: string; cur: number; prev: number }) {
  return (
    <div className="a-card a-kpi">
      <p className="a-kpi-label">{label}</p>
      <p className="a-kpi-value" dir="ltr" style={{ textAlign: 'start' }}>
        {value}
      </p>
      <Delta cur={cur} prev={prev} />
    </div>
  )
}

/** Single-series column chart: thin bars, rounded data-end, 1px recessive grid, per-bar tooltip. */
function RevenueChart({ series, lang }: { series: D['series']; lang: string }) {
  const [hover, setHover] = useState<number | null>(null)
  const W = 720
  const H = 220
  const padL = 44
  const padB = 24
  const padT = 8
  const innerW = W - padL - 8
  const innerH = H - padB - padT
  const max = Math.max(1, ...series.map((d) => d.revenue))
  // clean ticks
  const rawStep = max / 4
  const mag = 10 ** Math.floor(Math.log10(rawStep))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= rawStep) ?? rawStep
  const top = step * 4
  const band = innerW / series.length
  const barW = Math.min(24, Math.max(2, band - 2))
  const y = (v: number) => padT + innerH - (v / top) * innerH
  const labelEvery = Math.ceil(series.length / 8)
  const rtl = lang === 'ar'
  const x = (i: number) => padL + (rtl ? series.length - 1 - i : i) * band + band / 2

  return (
    <div className="a-chart" onMouseLeave={() => setHover(null)}>
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Revenue by day">
        {[0, 1, 2, 3, 4].map((k) => (
          <g key={k}>
            <line className="a-grid-line" x1={padL} x2={W - 8} y1={y(step * k)} y2={y(step * k)} />
            <text className="a-tick" x={padL - 8} y={y(step * k) + 3} textAnchor="end">
              {compact(step * k)}
            </text>
          </g>
        ))}
        {series.map((d, i) => {
          const h = Math.max(d.revenue > 0 ? 2 : 0, innerH - (y(d.revenue) - padT))
          const cx = x(i)
          const r = Math.min(4, barW / 2, h)
          const x0 = cx - barW / 2
          const y0 = padT + innerH - h
          // rounded top, square at the baseline
          const path = h <= 0 ? '' : `M${x0},${padT + innerH} V${y0 + r} Q${x0},${y0} ${x0 + r},${y0} H${x0 + barW - r} Q${x0 + barW},${y0} ${x0 + barW},${y0 + r} V${padT + innerH} Z`
          return (
            <g key={d.date}>
              <rect className="a-hit" x={cx - band / 2} y={padT} width={band} height={innerH} onMouseEnter={() => setHover(i)} onFocus={() => setHover(i)} tabIndex={-1} />
              {path && <path className="a-bar" d={path} data-active={hover === i} data-dim={hover != null && hover !== i} pointerEvents="none" />}
              {i % labelEvery === 0 && (
                <text className="a-tick" x={cx} y={H - 6} textAnchor="middle">
                  {d.date.slice(5).replace('-', '/')}
                </text>
              )}
            </g>
          )
        })}
      </svg>
      {hover != null && (
        <div className="a-tip" style={{ left: `${(x(hover) / W) * 100}%`, top: `${(y(series[hover].revenue) / H) * 100}%` }}>
          <b dir="ltr">{egp(series[hover].revenue)}</b>
          {fmtDate(series[hover].date, lang)} · {series[hover].orders}
        </div>
      )}
    </div>
  )
}

export default function Dashboard() {
  const { t, lang } = useT()
  const navigate = useNavigate()
  const [days, setDays] = useState(30)
  const { data, isLoading } = useQuery({ queryKey: ['admin', 'dashboard', days], queryFn: () => adminApi.dashboard(days), refetchInterval: 120_000 })

  const { data: returnCount = 0 } = useQuery({
    queryKey: ['admin', 'returns-count'],
    queryFn: () => adminApi.returns({ status: 'REQUESTED' }).then((r) => r.total),
  })
  const k = (key: keyof Kpis) => ({ cur: data?.current[key] ?? 0, prev: data?.previous[key] ?? 0 })

  return (
    <>
      <PageHead title={t.dashboard}>
        <div className="a-chips" role="group">
          {RANGES.map((d) => (
            <button key={d} type="button" className="a-chip" aria-pressed={days === d} onClick={() => setDays(d)}>
              {t.last(d)}
            </button>
          ))}
        </div>
      </PageHead>

      <div style={{ marginBottom: 16 }}>
        <StoreSwitch />
      </div>

      {isLoading || !data ? (
        <p className="a-muted">{t.loading}</p>
      ) : (
        <div className="a-stack">
          {(data.statusCounts.NEW > 0 || data.statusCounts.CONFIRMED > 0 || returnCount > 0) && (
            <div className="a-card" style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>
              <b style={{ fontWeight: 500, marginInlineEnd: 6 }}>{t.needsAction}</b>
              {data.statusCounts.NEW > 0 && (
                <Link className="a-btn a-btn-sm" to="/admin/orders?status=NEW">
                  <Badge s="NEW">{t.newOrders(data.statusCounts.NEW)}</Badge>
                </Link>
              )}
              {data.statusCounts.CONFIRMED > 0 && (
                <Link className="a-btn a-btn-sm" to="/admin/orders?status=CONFIRMED">
                  <Badge s="CONFIRMED">{t.toShip(data.statusCounts.CONFIRMED)}</Badge>
                </Link>
              )}
              {returnCount > 0 && (
                <Link className="a-btn a-btn-sm" to="/admin/returns">
                  <Badge s="NEW">{t.newReturns(returnCount)}</Badge>
                </Link>
              )}
            </div>
          )}

          <div className="a-grid a-cols-4">
            <Kpi label={t.revenue} value={egp(data.current.revenue)} {...k('revenue')} />
            <Kpi label={t.ordersCount} value={String(data.current.orders)} {...k('orders')} />
            <Kpi label={t.aov} value={egp(data.current.aov)} {...k('aov')} />
            <Kpi label={t.itemsSold} value={String(data.current.itemsSold)} {...k('itemsSold')} />
          </div>

          <div className="a-card">
            <h2>{t.salesByDay}</h2>
            <RevenueChart series={data.series} lang={lang} />
            <details style={{ marginTop: 10 }}>
              <summary className="a-muted" style={{ cursor: 'pointer', fontSize: 12 }}>
                {t.showTable}
              </summary>
              <div className="a-table-wrap" style={{ marginTop: 10, maxHeight: 280, overflowY: 'auto' }}>
                <table className="a-table">
                  <thead>
                    <tr>
                      <th>{t.date}</th>
                      <th className="a-num">{t.ordersCount}</th>
                      <th className="a-num">{t.revenue}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[...data.series].reverse().map((d) => (
                      <tr key={d.date}>
                        <td>{fmtDate(d.date, lang)}</td>
                        <td className="a-num">{d.orders}</td>
                        <td className="a-num" dir="ltr">{egp(d.revenue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </details>
          </div>

          <div className="a-grid a-cols-2">
            <div className="a-card">
              <h2>{t.bestSellers}</h2>
              {data.bestSellers.length === 0 ? (
                <p className="a-muted">{t.empty}</p>
              ) : (
                <table className="a-table">
                  <tbody>
                    {data.bestSellers.map((b) => (
                      <tr key={b.slug}>
                        <td>{b.name}</td>
                        <td className="a-num a-muted">
                          {b.qty} {t.pieces}
                        </td>
                        <td className="a-num" dir="ltr">{egp(b.revenue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            <div className="a-card">
              <h2>{t.lowStock}</h2>
              {data.lowStock.length === 0 ? (
                <p className="a-muted">{t.noLowStock}</p>
              ) : (
                <table className="a-table">
                  <tbody>
                    {data.lowStock.map((s) => (
                      <tr key={`${s.slug}-${s.size}`} data-href onClick={() => navigate(`/admin/products/${s.productId}`)}>
                        <td>{lang === 'ar' ? s.name.ar || s.name.en : s.name.en}</td>
                        <td className="a-mono">{s.size}</td>
                        <td className="a-num">
                          <Badge s={s.stock === 0 ? 'off' : 'NEW'}>{s.stock}</Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>

          <div className="a-card" style={{ padding: 0 }}>
            <h2 style={{ padding: '18px 18px 0' }}>
              {t.recentOrders}
              <Link className="a-btn a-btn-sm" to="/admin/orders">
                {t.orders}
              </Link>
            </h2>
            <RecentTable rows={data.recent} />
          </div>
        </div>
      )}
    </>
  )
}

function RecentTable({ rows }: { rows: D['recent'] }) {
  const { t, lang } = useT()
  const navigate = useNavigate()
  if (!rows.length) return <p className="a-empty">{t.empty}</p>
  return (
    <div style={{ overflowX: 'auto' }}>
      <table className="a-table">
        <tbody>
          {rows.map((o) => (
            <tr key={o.number} data-href onClick={() => navigate(`/admin/orders/${o.number}`)}>
              <td className="a-mono">{o.number}</td>
              <td>{o.name}</td>
              <td className="a-muted a-hide-sm">{fmtDate(o.createdAt, lang, true)}</td>
              <td>
                <Badge s={o.status}>{t.statusLabel[o.status]}</Badge>
              </td>
              <td className="a-num" dir="ltr">{egp(o.total)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
