import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { assetUrl } from '../../api/http'
import { PRODUCT_ART } from '../../components/productArt'
import { useToast } from '../../store/toast'
import { adminApi, egp, fmtDate, type AdminOrder } from '../api'
import { useT } from '../i18n'
import { Area, Badge, Field, Modal, PageHead, errText } from '../ui'

const intlPhone = (p: string) => (p.startsWith('0') ? `20${p.slice(1)}` : p)

function Invoice({ o }: { o: AdminOrder }) {
  return (
    <div className="a-invoice" dir="ltr">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <h1>VOID</h1>
        <div style={{ textAlign: 'right' }}>
          <b>Invoice {o.number}</b>
          <br />
          {fmtDate(o.createdAt, 'en', true)}
        </div>
      </div>
      <div className="meta">
        <div>
          <b>Deliver to</b>
          <br />
          {o.name} · {o.phone}
          <br />
          {o.street}
          {o.building ? `, ${o.building}` : ''}
          <br />
          {o.city}, {o.governorate}
          {o.addressNotes && (
            <>
              <br />
              {o.addressNotes}
            </>
          )}
        </div>
        <div style={{ textAlign: 'right' }}>
          <b>Payment</b>
          <br />
          {o.paymentMethod === 'COD' ? 'Cash on delivery' : o.paymentMethod}
          <br />
          <b style={{ fontSize: 16 }}>Collect: {egp(o.paymentStatus === 'PAID' ? 0 : o.total)}</b>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th>Item</th>
            <th>Size</th>
            <th>SKU</th>
            <th className="r">Qty</th>
            <th className="r">Price</th>
            <th className="r">Total</th>
          </tr>
        </thead>
        <tbody>
          {o.items.map((i) => (
            <tr key={`${i.slug}-${i.size}`}>
              <td>{i.name}</td>
              <td>{i.size}</td>
              <td>{i.sku ?? '—'}</td>
              <td className="r">{i.qty}</td>
              <td className="r">{egp(i.unitPrice)}</td>
              <td className="r">{egp(i.lineTotal)}</td>
            </tr>
          ))}
          <tr>
            <td colSpan={5} className="r">Subtotal</td>
            <td className="r">{egp(o.subtotal)}</td>
          </tr>
          {o.discount > 0 && (
            <tr>
              <td colSpan={5} className="r">Discount {o.couponCode && `(${o.couponCode})`}</td>
              <td className="r">−{egp(o.discount)}</td>
            </tr>
          )}
          <tr>
            <td colSpan={5} className="r">Shipping</td>
            <td className="r">{egp(o.shipping)}</td>
          </tr>
          <tr>
            <td colSpan={5} className="r">
              <b>Total</b>
            </td>
            <td className="r">
              <b>{egp(o.total)}</b>
            </td>
          </tr>
        </tbody>
      </table>
      {o.customerNote && <p>Note: {o.customerNote}</p>}
      <p style={{ marginTop: 24, color: '#666' }}>Thank you for shopping VOID.</p>
    </div>
  )
}

export default function OrderDetail() {
  const { number = '' } = useParams()
  const { t, lang } = useT()
  const qc = useQueryClient()
  const flash = useToast()
  const { data: o, isLoading, error } = useQuery({ queryKey: ['admin', 'order', number], queryFn: () => adminApi.order(number) })
  const { data: linkedReturns = [] } = useQuery({ queryKey: ['admin', 'returns', 'order', number], queryFn: () => adminApi.returnsForOrder(number) })
  const [pending, setPending] = useState<string | null>(null)
  const [statusNote, setStatusNote] = useState('')
  const [note, setNote] = useState('')
  const [editing, setEditing] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  const onUpdated = (u: AdminOrder) => {
    qc.setQueryData(['admin', 'order', number], u)
    qc.invalidateQueries({ queryKey: ['admin', 'orders'] })
    qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
    qc.invalidateQueries({ queryKey: ['admin', 'new-count'] })
  }
  const setStatus = useMutation({
    mutationFn: (s: string) => adminApi.setStatus(number, s, statusNote.trim() || undefined),
    onSuccess: (u) => {
      onUpdated(u)
      setPending(null)
      setStatusNote('')
      flash(t.saved)
    },
    onError: (e) => setErr(errText(e, t)),
  })
  const addNote = useMutation({
    mutationFn: () => adminApi.addNote(number, note.trim()),
    onSuccess: (u) => {
      onUpdated(u)
      setNote('')
    },
    onError: (e) => setErr(errText(e, t)),
  })

  if (isLoading) return <p className="a-muted">{t.loading}</p>
  if (error || !o) return <p className="a-error">{errText(error, t)}</p>

  return (
    <>
      <Link to="/admin/orders" className="a-btn a-btn-sm" style={{ marginBottom: 14 }}>
        {lang === 'ar' ? '→' : '←'} {t.orders}
      </Link>
      <PageHead
        title={
          <span className="a-row" style={{ flexWrap: 'wrap' }}>
            <span className="a-mono" style={{ fontSize: 20 }}>{o.number}</span>
            <Badge s={o.status}>{t.statusLabel[o.status]}</Badge>
            <Badge s={o.paymentStatus}>{t.paymentStatus[o.paymentStatus]}</Badge>
          </span>
        }
        sub={fmtDate(o.createdAt, lang, true)}
      >
        <button type="button" className="a-btn" onClick={() => window.print()}>
          {t.print}
        </button>
        {o.nextStatuses.map((s) => (
          <button key={s} type="button" className={`a-btn ${s === 'CANCELLED' || s === 'RETURNED' ? 'a-btn-danger' : 'a-btn-primary'}`} onClick={() => { setErr(null); setPending(s) }}>
            {t.action[s]}
          </button>
        ))}
      </PageHead>
      {err && <p className="a-error" style={{ marginBottom: 14 }}>{err}</p>}

      <div className="a-split">
        <div className="a-stack">
          <div className="a-card">
            <h2>
              {t.items} <span className="a-muted">{o.items.reduce((n, i) => n + i.qty, 0)}</span>
            </h2>
            <table className="a-table">
              <tbody>
                {o.items.map((i) => {
                  const art = PRODUCT_ART[i.slug]
                  return (
                    <tr key={`${i.slug}-${i.size}`}>
                      <td>
                        <div className="a-row">
                          <span className="a-thumb">
                            {i.image ? <img src={assetUrl(i.image)} alt="" /> : art ? <svg viewBox="0 0 240 280" style={{ width: '100%', height: '100%' }} dangerouslySetInnerHTML={{ __html: art.front }} /> : null}
                          </span>
                          <span>
                            {i.name}
                            <div className="a-faint a-mono">
                              {i.size}
                              {i.sku ? ` · ${i.sku}` : ''}
                            </div>
                          </span>
                        </div>
                      </td>
                      <td className="a-num a-muted" dir="ltr">
                        {egp(i.unitPrice)} × {i.qty}
                      </td>
                      <td className="a-num" dir="ltr">{egp(i.lineTotal)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            <dl className="a-dl" style={{ marginTop: 14, maxWidth: 320, marginInlineStart: 'auto' }}>
              <dt>{t.subtotal}</dt>
              <dd className="a-num" dir="ltr">{egp(o.subtotal)}</dd>
              {o.discount > 0 && (
                <>
                  <dt>
                    {t.discount} <span className="a-mono">{o.couponCode}</span>
                  </dt>
                  <dd className="a-num" dir="ltr">−{egp(o.discount)}</dd>
                </>
              )}
              <dt>{t.shippingFee}</dt>
              <dd className="a-num" dir="ltr">{egp(o.shipping)}</dd>
              <dt style={{ color: 'var(--text)', fontWeight: 600 }}>{t.total}</dt>
              <dd className="a-num" dir="ltr" style={{ fontWeight: 600 }}>{egp(o.total)}</dd>
            </dl>
          </div>

          <div className="a-card">
            <h2>{t.activity}</h2>
            <ol className="a-feed">
              {[...o.events].reverse().map((e, i) => (
                <li key={i}>
                  <b style={{ fontWeight: 500 }}>
                    {e.type === 'STATUS' && e.toStatus ? `${t.statusLabel[e.fromStatus ?? ''] ?? ''} → ${t.statusLabel[e.toStatus]}` : t.event[e.type] ?? e.type}
                  </b>
                  {e.actor && <span className="a-muted"> · {e.actor}</span>}
                  <time>{fmtDate(e.at, lang, true)}</time>
                  {e.note && <p>{e.note}</p>}
                </li>
              ))}
            </ol>
            <form
              className="a-form"
              onSubmit={(e: FormEvent) => {
                e.preventDefault()
                if (note.trim()) addNote.mutate()
              }}
            >
              <Area label={t.addNote} placeholder={t.notePh} value={note} onChange={(e) => setNote(e.target.value)} rows={2} maxLength={1000} />
              <div>
                <button type="submit" className="a-btn a-btn-sm" disabled={!note.trim() || addNote.isPending}>
                  {t.add}
                </button>
              </div>
            </form>
          </div>
        </div>

        <div className="a-stack">
          <div className="a-card">
            <h2>{t.customer}</h2>
            <dl className="a-dl">
              <dt>{t.customer}</dt>
              <dd>{o.customerId ? <Link to={`/admin/customers/${o.customerId}`} style={{ textDecoration: 'underline' }}>{o.name}</Link> : <>{o.name} <span className="a-faint">· {t.guest}</span></>}</dd>
              <dt>{t.phone}</dt>
              <dd className="a-mono" dir="ltr" style={{ textAlign: 'start' }}>{o.phone}</dd>
              <dt>{t.email}</dt>
              <dd dir="ltr" style={{ textAlign: 'start' }}>{o.email}</dd>
            </dl>
            <div className="a-actions" style={{ marginTop: 14 }}>
              <a className="a-btn a-btn-sm" href={`tel:${o.phone}`}>
                {t.call}
              </a>
              <a className="a-btn a-btn-sm" href={`https://wa.me/${intlPhone(o.phone)}`} target="_blank" rel="noopener noreferrer">
                {t.whatsapp}
              </a>
            </div>
          </div>
          <div className="a-card">
            <h2>
              {t.delivery}
              {(o.status === 'NEW' || o.status === 'CONFIRMED') && (
                <button type="button" className="a-btn a-btn-sm" onClick={() => setEditing(true)}>
                  {t.edit}
                </button>
              )}
            </h2>
            <p style={{ margin: 0, lineHeight: 1.7 }}>
              {o.street}
              {o.building ? `, ${o.building}` : ''}
              <br />
              {o.city}, <span className="a-mono">{o.governorate}</span>
            </p>
            {o.addressNotes && <p className="a-muted" style={{ margin: '8px 0 0' }}>{o.addressNotes}</p>}
          </div>
          {linkedReturns.length > 0 && (
            <div className="a-card">
              <h2>{t.returns}</h2>
              {linkedReturns.map((r) => (
                <Link key={r.number} to={`/admin/returns/${r.number}`} className="a-row" style={{ justifyContent: 'space-between', padding: '6px 0' }}>
                  <span>
                    <span className="a-mono">{r.number}</span> · {t.typeLabel[r.type]}
                  </span>
                  <Badge s={r.status === 'REQUESTED' ? 'NEW' : r.status === 'COMPLETED' ? 'DELIVERED' : r.status === 'REJECTED' ? 'CANCELLED' : 'CONFIRMED'}>
                    {t.returnStatusLabel[r.status]}
                  </Badge>
                </Link>
              ))}
            </div>
          )}
          <div className="a-card">
            <h2>{t.payment}</h2>
            <p style={{ margin: 0 }}>{o.paymentMethod === 'COD' ? t.cod : o.paymentMethod}</p>
            {o.paymentRef && <p className="a-faint a-mono">{o.paymentRef}</p>}
          </div>
          {o.customerNote && (
            <div className="a-card">
              <h2>{t.customerNote}</h2>
              <p style={{ margin: 0, whiteSpace: 'pre-line' }}>{o.customerNote}</p>
            </div>
          )}
        </div>
      </div>

      {pending && (
        <Modal title={t.action[pending]} onClose={() => setPending(null)}>
          <div className="a-form">
            {pending === 'CANCELLED' && <p className="a-info">{t.confirmCancel}</p>}
            <Area label={t.statusNote} value={statusNote} onChange={(e) => setStatusNote(e.target.value)} rows={2} maxLength={1000} />
            {err && <p className="a-error">{err}</p>}
            <div className="a-actions">
              <button type="button" className={`a-btn ${pending === 'CANCELLED' || pending === 'RETURNED' ? 'a-btn-danger' : 'a-btn-primary'}`} disabled={setStatus.isPending} onClick={() => setStatus.mutate(pending)}>
                {t.action[pending]}
              </button>
              <button type="button" className="a-btn" onClick={() => setPending(null)}>
                {t.cancel}
              </button>
            </div>
          </div>
        </Modal>
      )}
      {editing && <EditDelivery o={o} onClose={() => setEditing(false)} onSaved={onUpdated} />}
      <Invoice o={o} />
    </>
  )
}

function EditDelivery({ o, onClose, onSaved }: { o: AdminOrder; onClose: () => void; onSaved: (o: AdminOrder) => void }) {
  const { t } = useT()
  const [f, setF] = useState({ name: o.name, phone: o.phone, city: o.city, street: o.street, building: o.building, addressNotes: o.addressNotes })
  const [err, setErr] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => adminApi.editAddress(o.number, f),
    onSuccess: (u) => {
      onSaved(u)
      onClose()
    },
    onError: (e) => setErr(errText(e, t)),
  })
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }))
  return (
    <Modal title={t.editDelivery} onClose={onClose}>
      <form className="a-form" onSubmit={(e) => { e.preventDefault(); save.mutate() }}>
        <Field label={t.customer} value={f.name} onChange={set('name')} required />
        <Field label={t.phone} value={f.phone} onChange={set('phone')} dir="ltr" required />
        <Field label={t.city} value={f.city} onChange={set('city')} required />
        <Field label={t.street} value={f.street} onChange={set('street')} required />
        <Field label={t.building} value={f.building} onChange={set('building')} />
        <Field label={t.notes} value={f.addressNotes} onChange={set('addressNotes')} />
        {err && <p className="a-error">{err}</p>}
        <div className="a-actions">
          <button type="submit" className="a-btn a-btn-primary" disabled={save.isPending}>
            {t.save}
          </button>
          <button type="button" className="a-btn" onClick={onClose}>
            {t.cancel}
          </button>
        </div>
      </form>
    </Modal>
  )
}
