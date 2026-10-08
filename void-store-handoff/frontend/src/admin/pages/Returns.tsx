import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { assetUrl } from '../../api/http'
import { PRODUCT_ART } from '../../components/productArt'
import { useToast } from '../../store/toast'
import { adminApi, egp, fmtDate, fromPiastres, toPiastres } from '../api'
import { useT } from '../i18n'
import { Area, Badge, Field, Modal, PageHead, Pager, errText } from '../ui'

const STATUSES = ['ALL', 'REQUESTED', 'APPROVED', 'RECEIVED', 'COMPLETED', 'REJECTED', 'CANCELLED']
// map request status → badge colour family used across the admin
const tone = (s: string) => (s === 'REQUESTED' ? 'NEW' : s === 'COMPLETED' ? 'DELIVERED' : s === 'REJECTED' || s === 'CANCELLED' ? 'CANCELLED' : s === 'APPROVED' ? 'SHIPPED' : 'CONFIRMED')

function Detail({ number }: { number: string }) {
  const { t, lang } = useT()
  const qc = useQueryClient()
  const flash = useToast()
  const { data: r, isLoading, error } = useQuery({ queryKey: ['admin', 'return', number], queryFn: () => adminApi.returnDetail(number) })
  const [pending, setPending] = useState<string | null>(null)
  const [note, setNote] = useState('')
  const [refund, setRefund] = useState('')
  const [err, setErr] = useState<string | null>(null)

  const act = useMutation({
    mutationFn: (s: string) => {
      const amount = s === 'COMPLETED' && r?.type === 'RETURN' ? toPiastres(refund) : undefined
      if (Number.isNaN(amount)) throw new Error(t.refundAmount)
      return adminApi.setReturnStatus(number, s, note.trim() || undefined, amount)
    },
    onSuccess: (d) => {
      qc.setQueryData(['admin', 'return', number], d)
      qc.invalidateQueries({ queryKey: ['admin', 'returns'] })
      qc.invalidateQueries({ queryKey: ['admin', 'returns-count'] })
      qc.invalidateQueries({ queryKey: ['admin', 'products'] })
      setPending(null)
      setNote('')
      flash(t.saved)
    },
    onError: (e) => setErr(e instanceof Error && !('status' in e) ? e.message : errText(e, t)),
  })

  if (isLoading) return <p className="a-muted">{t.loading}</p>
  if (!r) return <p className="a-error">{errText(error, t)}</p>

  return (
    <>
      <Link to="/admin/returns" className="a-btn a-btn-sm" style={{ marginBottom: 14 }}>
        {lang === 'ar' ? '→' : '←'} {t.returns}
      </Link>
      <PageHead
        title={
          <span className="a-row" style={{ flexWrap: 'wrap' }}>
            <span className="a-mono" style={{ fontSize: 20 }}>{r.number}</span>
            <Badge s={tone(r.status)}>{t.returnStatusLabel[r.status]}</Badge>
            <Badge s="CONFIRMED">{t.typeLabel[r.type]}</Badge>
          </span>
        }
        sub={fmtDate(r.createdAt, lang, true)}
      >
        {r.nextStatuses.map((s) => (
          <button
            key={s}
            type="button"
            className={`a-btn ${s === 'REJECTED' ? 'a-btn-danger' : 'a-btn-primary'}`}
            onClick={() => {
              setErr(null)
              setRefund(fromPiastres(r.itemsValue))
              setPending(s)
            }}
          >
            {t.returnAction[s]}
          </button>
        ))}
      </PageHead>
      {err && <p className="a-error" style={{ marginBottom: 14 }}>{err}</p>}

      <div className="a-split">
        <div className="a-stack">
          <div className="a-card">
            <h2>{t.items}</h2>
            <table className="a-table">
              <tbody>
                {r.items.map(({ item: i, exchangeStock }) => {
                  const art = PRODUCT_ART[i.slug]
                  return (
                    <tr key={i.orderItemId}>
                      <td>
                        <div className="a-row">
                          <span className="a-thumb">
                            {i.image ? <img src={assetUrl(i.image)} alt="" /> : art ? <svg viewBox="0 0 240 280" style={{ width: '100%', height: '100%' }} dangerouslySetInnerHTML={{ __html: art.front }} /> : null}
                          </span>
                          <span>
                            {i.name}
                            <div className="a-faint a-mono">
                              {i.size} × {i.qty}
                            </div>
                          </span>
                        </div>
                      </td>
                      <td>
                        {i.exchangeSize ? (
                          <>
                            <span className="a-muted">{t.exchangeTo} </span>
                            <b className="a-mono">{i.exchangeSize}</b>
                            <div>
                              <Badge s={(exchangeStock ?? 0) >= i.qty ? 'on' : 'off'}>{t.inStock(exchangeStock ?? 0)}</Badge>
                            </div>
                          </>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="a-num" dir="ltr">{egp(i.unitPrice * i.qty)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            <dl className="a-dl" style={{ marginTop: 14 }}>
              <dt>{t.reason}</dt>
              <dd>{t.reasonLabel[r.reason]}</dd>
              <dt>{t.piecesValue}</dt>
              <dd dir="ltr" style={{ textAlign: 'start' }}>{egp(r.itemsValue)}</dd>
              {r.refundAmount != null && (
                <>
                  <dt>{t.refundAmount}</dt>
                  <dd dir="ltr" style={{ textAlign: 'start' }}>{egp(r.refundAmount)}</dd>
                </>
              )}
            </dl>
            {r.note && <p className="a-info" style={{ marginTop: 14, whiteSpace: 'pre-line' }}>{r.note}</p>}
          </div>
          <div className="a-card">
            <h2>{t.activity}</h2>
            <ol className="a-feed">
              {[...r.events].reverse().map((e, i) => (
                <li key={i}>
                  <b style={{ fontWeight: 500 }}>
                    {e.fromStatus ? `${t.returnStatusLabel[e.fromStatus]} → ` : ''}
                    {t.returnStatusLabel[e.toStatus]}
                  </b>
                  {e.actor && <span className="a-muted"> · {e.actor}</span>}
                  <time>{fmtDate(e.at, lang, true)}</time>
                  {e.note && <p>{e.note}</p>}
                </li>
              ))}
            </ol>
          </div>
        </div>
        <div className="a-card">
          <h2>{t.customer}</h2>
          <dl className="a-dl">
            <dt>{t.customer}</dt>
            <dd>{r.customer}</dd>
            <dt>{t.phone}</dt>
            <dd className="a-mono" dir="ltr" style={{ textAlign: 'start' }}>{r.phone}</dd>
            <dt>{t.email}</dt>
            <dd dir="ltr" style={{ textAlign: 'start' }}>{r.email}</dd>
            <dt>{t.order}</dt>
            <dd>
              <Link to={`/admin/orders/${r.orderNumber}`} className="a-mono" style={{ textDecoration: 'underline' }}>
                {r.orderNumber}
              </Link>
            </dd>
          </dl>
          <div className="a-actions" style={{ marginTop: 14 }}>
            <a className="a-btn a-btn-sm" href={`tel:${r.phone}`}>
              {t.call}
            </a>
            <a className="a-btn a-btn-sm" href={`https://wa.me/${r.phone.startsWith('0') ? '20' + r.phone.slice(1) : r.phone}`} target="_blank" rel="noopener noreferrer">
              {t.whatsapp}
            </a>
          </div>
        </div>
      </div>

      {pending && (
        <Modal title={t.returnAction[pending]} onClose={() => setPending(null)}>
          <div className="a-form">
            <p className="a-info">{t.returnHelp[pending]}</p>
            {pending === 'COMPLETED' && r.type === 'RETURN' && (
              <Field label={t.refundAmount} value={refund} onChange={(e) => setRefund(e.target.value)} inputMode="decimal" dir="ltr" />
            )}
            <Area label={t.statusNote} value={note} onChange={(e) => setNote(e.target.value)} rows={2} maxLength={1000} />
            {err && <p className="a-error">{err}</p>}
            <div className="a-actions">
              <button type="button" className={`a-btn ${pending === 'REJECTED' ? 'a-btn-danger' : 'a-btn-primary'}`} disabled={act.isPending} onClick={() => act.mutate(pending)}>
                {t.returnAction[pending]}
              </button>
              <button type="button" className="a-btn" onClick={() => setPending(null)}>
                {t.cancel}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </>
  )
}

export default function Returns() {
  const { number } = useParams()
  const { t, lang } = useT()
  const navigate = useNavigate()
  const [status, setStatus] = useState('REQUESTED')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'returns', status, q, page],
    queryFn: () => adminApi.returns({ status: status === 'ALL' ? undefined : status, q: q.trim() || undefined, page }),
    placeholderData: keepPreviousData,
    enabled: !number,
  })

  if (number) return <Detail number={number} />
  return (
    <>
      <PageHead title={t.returns} />
      <div className="a-filters">
        <input className="a-input a-search" type="search" placeholder={t.searchReturns} value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} aria-label={t.search} />
      </div>
      <div className="a-chips" style={{ marginBottom: 16 }}>
        {STATUSES.map((s) => (
          <button key={s} type="button" className="a-chip" aria-pressed={status === s} onClick={() => { setStatus(s); setPage(0) }}>
            {s === 'ALL' ? t.all : t.returnStatusLabel[s]}
          </button>
        ))}
      </div>
      <div className="a-table-wrap">
        <table className="a-table">
          <thead>
            <tr>
              <th>{t.returnRequest}</th>
              <th>{t.customer}</th>
              <th>{t.type}</th>
              <th>{t.status}</th>
              <th className="a-hide-sm">{t.placed}</th>
              <th className="a-num">{t.piecesValue}</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((r) => (
              <tr key={r.number} data-href tabIndex={0} onClick={() => navigate(`/admin/returns/${r.number}`)} onKeyDown={(e) => e.key === 'Enter' && navigate(`/admin/returns/${r.number}`)}>
                <td>
                  <span className="a-mono">{r.number}</span>
                  <div className="a-faint a-mono">{r.orderNumber}</div>
                </td>
                <td>
                  {r.customer}
                  <div className="a-faint">{t.reasonLabel[r.reason]}</div>
                </td>
                <td>{t.typeLabel[r.type]}</td>
                <td>
                  <Badge s={tone(r.status)}>{t.returnStatusLabel[r.status]}</Badge>
                </td>
                <td className="a-hide-sm a-muted">{fmtDate(r.createdAt, lang, true)}</td>
                <td className="a-num" dir="ltr">
                  {egp(r.value)}
                  <div className="a-faint" style={{ fontSize: 12 }}>
                    {r.pieces} {t.pieces}
                  </div>
                </td>
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
