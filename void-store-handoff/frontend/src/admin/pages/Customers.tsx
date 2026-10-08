import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../../store/auth'
import { adminApi, egp, fmtDate } from '../api'
import { useT } from '../i18n'
import { Badge, PageHead, Pager, errText } from '../ui'

function CustomerDetail({ id }: { id: number }) {
  const { t, lang } = useT()
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data: c, isLoading, error } = useQuery({ queryKey: ['admin', 'customer', id], queryFn: () => adminApi.customer(id) })
  const toggle = useMutation({
    mutationFn: (v: boolean) => adminApi.setCustomerEnabled(id, v),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'customer', id] }),
  })
  if (isLoading) return <p className="a-muted">{t.loading}</p>
  if (!c) return <p className="a-error">{errText(error, t)}</p>
  const canToggle = user?.role === 'ADMIN' || user?.role === 'OWNER'
  return (
    <>
      <Link to="/admin/customers" className="a-btn a-btn-sm" style={{ marginBottom: 14 }}>
        {t.customers}
      </Link>
      <PageHead title={c.name} sub={<span dir="ltr">{c.email}</span>}>
        {!c.enabled && <Badge s="off">{t.disabled}</Badge>}
        {canToggle && (
          <button type="button" className={`a-btn ${c.enabled ? 'a-btn-danger' : ''}`} onClick={() => toggle.mutate(!c.enabled)}>
            {c.enabled ? t.disable : t.enable}
          </button>
        )}
      </PageHead>
      <div className="a-split">
        <div className="a-card" style={{ padding: 0 }}>
          <h2 style={{ padding: '18px 18px 0' }}>
            {t.orders} <span className="a-muted">{c.orderCount}</span>
          </h2>
          {c.orders.length === 0 ? (
            <p className="a-empty">{t.empty}</p>
          ) : (
            <table className="a-table">
              <tbody>
                {c.orders.map((o) => (
                  <tr key={o.number} data-href onClick={() => navigate(`/admin/orders/${o.number}`)}>
                    <td className="a-mono">{o.number}</td>
                    <td className="a-muted">{fmtDate(o.createdAt, lang)}</td>
                    <td>
                      <Badge s={o.status}>{t.statusLabel[o.status]}</Badge>
                    </td>
                    <td className="a-num" dir="ltr">{egp(o.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        <div className="a-stack">
          <div className="a-card">
            <dl className="a-dl">
              <dt>{t.phone}</dt>
              <dd className="a-mono" dir="ltr" style={{ textAlign: 'start' }}>{c.phone ?? '—'}</dd>
              <dt>{t.spent}</dt>
              <dd dir="ltr" style={{ textAlign: 'start' }}>{egp(c.spent)}</dd>
              <dt>{t.joined}</dt>
              <dd>{fmtDate(c.createdAt, lang)}</dd>
              <dt>{t.lastLogin}</dt>
              <dd>{fmtDate(c.lastLoginAt, lang, true)}</dd>
            </dl>
          </div>
          {c.addresses.length > 0 && (
            <div className="a-card">
              <h2>{t.addresses}</h2>
              {c.addresses.map((a, i) => (
                <p key={i} style={{ margin: '0 0 12px', lineHeight: 1.6, fontSize: 13 }}>
                  {a.name} · <span dir="ltr">{a.phone}</span>
                  <br />
                  <span className="a-muted">
                    {a.street}
                    {a.building ? `, ${a.building}` : ''}, {a.city}, {a.governorate}
                  </span>
                </p>
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  )
}

export default function Customers() {
  const { id } = useParams()
  const { t, lang } = useT()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [debounced, setDebounced] = useState('')
  const [page, setPage] = useState(0)
  useEffect(() => {
    const h = setTimeout(() => {
      setDebounced(q.trim())
      setPage(0)
    }, 300)
    return () => clearTimeout(h)
  }, [q])
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'customers', debounced, page],
    queryFn: () => adminApi.customers(debounced, page),
    placeholderData: keepPreviousData,
    enabled: !id,
  })

  if (id) return <CustomerDetail id={Number(id)} />
  return (
    <>
      <PageHead title={t.customers} />
      <div className="a-filters">
        <input className="a-input a-search" type="search" placeholder={t.searchCustomers} value={q} onChange={(e) => setQ(e.target.value)} aria-label={t.search} />
      </div>
      <div className="a-table-wrap">
        <table className="a-table">
          <thead>
            <tr>
              <th>{t.customer}</th>
              <th className="a-hide-sm">{t.phone}</th>
              <th className="a-hide-sm">{t.joined}</th>
              <th className="a-num">{t.orders}</th>
              <th className="a-num">{t.spent}</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((c) => (
              <tr key={c.id} data-href tabIndex={0} onClick={() => navigate(`/admin/customers/${c.id}`)} onKeyDown={(e) => e.key === 'Enter' && navigate(`/admin/customers/${c.id}`)}>
                <td>
                  {c.name}
                  <div className="a-faint" dir="ltr" style={{ textAlign: 'start', fontSize: 12 }}>
                    {c.email}
                  </div>
                </td>
                <td className="a-hide-sm a-mono" dir="ltr" style={{ textAlign: 'start' }}>{c.phone ?? '—'}</td>
                <td className="a-hide-sm a-muted">{fmtDate(c.createdAt, lang)}</td>
                <td className="a-num">{c.orders}</td>
                <td className="a-num" dir="ltr">{egp(c.spent)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!isLoading && data?.items.length === 0 && <p className="a-empty">{t.empty}</p>}
      </div>
      {data && <Pager page={data.page} total={data.total} size={data.size} onPage={setPage} />}
    </>
  )
}
