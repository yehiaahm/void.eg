import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { assetUrl } from '../../api/http'
import { PRODUCT_ART } from '../../components/productArt'
import { useAuth } from '../../store/auth'
import { adminApi, egp } from '../api'
import { useT } from '../i18n'
import { Badge, PageHead } from '../ui'

const FILTERS = ['ALL', 'ACTIVE', 'DRAFT', 'ARCHIVED']

export default function Products() {
  const { t, lang } = useT()
  const navigate = useNavigate()
  const { user } = useAuth()
  const canEdit = user?.role === 'ADMIN' || user?.role === 'OWNER'
  const { data = [], isLoading } = useQuery({ queryKey: ['admin', 'products'], queryFn: adminApi.products })
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('ALL')

  const rows = useMemo(() => {
    const n = q.trim().toLowerCase()
    return data.filter(
      (p) => (status === 'ALL' || p.status === status) && (!n || p.name.en.toLowerCase().includes(n) || p.name.ar.includes(n) || p.slug.includes(n)),
    )
  }, [data, q, status])

  return (
    <>
      <PageHead title={t.products} sub={t.results(data.length)}>
        {canEdit && (
          <Link className="a-btn a-btn-primary" to="/admin/products/new">
            {t.newProduct}
          </Link>
        )}
      </PageHead>
      <div className="a-filters">
        <input className="a-input a-search" type="search" placeholder={t.search} value={q} onChange={(e) => setQ(e.target.value)} aria-label={t.search} />
        <div className="a-chips">
          {FILTERS.map((s) => (
            <button key={s} type="button" className="a-chip" aria-pressed={status === s} onClick={() => setStatus(s)}>
              {s === 'ALL' ? t.all : t.productStatus[s]}
              <span className="a-mono">{s === 'ALL' ? data.length : data.filter((p) => p.status === s).length}</span>
            </button>
          ))}
        </div>
      </div>
      <div className="a-table-wrap">
        <table className="a-table">
          <thead>
            <tr>
              <th>{t.product}</th>
              <th>{t.status}</th>
              <th className="a-hide-sm">{t.drop}</th>
              <th className="a-num a-hide-sm">{t.saves}</th>
              <th className="a-num">{t.stock}</th>
              <th className="a-num">{t.price}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((p) => {
              const art = PRODUCT_ART[p.slug]
              return (
                <tr key={p.id} data-href tabIndex={0} onClick={() => navigate(`/admin/products/${p.id}`)} onKeyDown={(e) => e.key === 'Enter' && navigate(`/admin/products/${p.id}`)}>
                  <td>
                    <div className="a-row">
                      <span className="a-thumb">
                        {p.image ? <img src={assetUrl(p.image)} alt="" /> : art ? <svg viewBox="0 0 240 280" style={{ width: '100%', height: '100%' }} dangerouslySetInnerHTML={{ __html: art.front }} /> : null}
                      </span>
                      <span>
                        {lang === 'ar' ? p.name.ar || p.name.en : p.name.en}
                        <div className="a-faint a-mono">{p.slug}</div>
                      </span>
                    </div>
                  </td>
                  <td>
                    <Badge s={p.status}>{t.productStatus[p.status]}</Badge>
                  </td>
                  <td className="a-hide-sm a-muted">{p.dropNumber != null ? String(p.dropNumber).padStart(2, '0') : '—'}</td>
                  <td className="a-num a-hide-sm a-muted">♥ {p.saves}</td>
                  <td className="a-num">
                    {p.totalStock}
                    {p.lowSizes > 0 && (
                      <div>
                        <Badge s="NEW">{t.lowSizes(p.lowSizes)}</Badge>
                      </div>
                    )}
                  </td>
                  <td className="a-num" dir="ltr">
                    {p.price == null ? <span className="a-faint">{t.noPrice}</span> : egp(p.price)}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
        {!isLoading && rows.length === 0 && <p className="a-empty">{t.empty}</p>}
        {isLoading && <p className="a-empty">{t.loading}</p>}
      </div>
    </>
  )
}
