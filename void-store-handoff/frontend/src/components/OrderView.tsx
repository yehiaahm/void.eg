import { useState, type FormEvent } from 'react'
import { ApiError, useProducts, useZones, type Order, type ReturnInput, type ReturnReason, type ReturnType } from '../api'
import { useI18n } from '../i18n'
import { errorMessage } from '../lib/errors'
import { formatPrice } from '../lib/format'
import { TextArea } from './Field'
import { LineItem } from './LineItem'

const FLOW = ['NEW', 'CONFIRMED', 'SHIPPED', 'DELIVERED'] as const
// Refunds only when the mistake is ours (returns policy); exchanges are open to any reason.
const OUR_MISTAKE: ReturnReason[] = ['WRONG_ITEM', 'DEFECT', 'NOT_AS_DESCRIBED']
const REASONS: Record<ReturnType, ReturnReason[]> = {
  EXCHANGE: ['SIZE', ...OUR_MISTAKE, 'CHANGED_MIND', 'OTHER'],
  RETURN: OUR_MISTAKE,
}

export function StatusPill({ status, label }: { status: string; label?: string }) {
  const { t } = useI18n()
  return (
    <span className="s-pill" data-s={status}>
      {label ?? t.status[status] ?? status}
    </span>
  )
}

export function formatDate(iso: string, lang: string) {
  return new Intl.DateTimeFormat(lang === 'ar' ? 'ar-EG-u-nu-latn' : 'en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'Africa/Cairo',
  }).format(new Date(iso))
}

interface Actions {
  /** Cancel a not-yet-confirmed order. */
  onCancel?: () => Promise<unknown>
  /** Send a return / exchange request. */
  onReturn?: (input: ReturnInput) => Promise<unknown>
}

/** Customer-facing order details (tracking page + account), with cancel / return actions. */
export function OrderView({ order, onCancel, onReturn }: { order: Order } & Actions) {
  const { lang, t, l } = useI18n()
  const { data: zones = [] } = useZones()
  const [returning, setReturning] = useState(false)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const zone = zones.find((z) => z.code === order.governorate)
  const reached = new Set(order.timeline.map((e) => e.toStatus))
  const stopped = order.status === 'CANCELLED' || order.status === 'RETURNED'
  const canReturn = !!onReturn && !!order.returnUntil && order.items.some((i) => i.returnableQty > 0)

  const cancel = async () => {
    if (!onCancel || !window.confirm(t.cancelOrderConfirm)) return
    setBusy(true)
    setMsg(null)
    try {
      await onCancel()
      setMsg({ ok: true, text: t.cancelled })
    } catch (e) {
      setMsg({ ok: false, text: errorMessage(e, t) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="s-form" style={{ gap: 24 }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
        <div>
          <p className="s-lab" style={{ margin: 0 }}>
            {t.orderNumber}
          </p>
          <p style={{ margin: '6px 0 0', fontFamily: 'var(--f-mono)', fontSize: 18 }} dir="ltr">
            {order.number}
          </p>
        </div>
        <StatusPill status={order.status} />
      </div>

      {!stopped && (
        <ol className="s-timeline" aria-label={t.status[order.status]}>
          {FLOW.map((s) => (
            <li key={s} data-done={reached.has(s) || FLOW.indexOf(s) <= FLOW.indexOf(order.status as (typeof FLOW)[number])}>
              {t.status[s]}
            </li>
          ))}
        </ol>
      )}

      {msg && (
        <p className={msg.ok ? 's-note' : 's-alert'} role="status">
          {msg.text}
        </p>
      )}

      {(order.canCancel && onCancel) || canReturn ? (
        <div className="s-card-box" style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center', justifyContent: 'space-between' }}>
          {order.canCancel && onCancel ? (
            <>
              <span className="s-sub" style={{ margin: 0 }}>
                {t.cancelHint}
              </span>
              <button type="button" className="s-cta" onClick={cancel} disabled={busy}>
                {t.cancelOrder}
              </button>
            </>
          ) : (
            <>
              <span className="s-sub" style={{ margin: 0 }}>
                {t.returnUntil(formatDate(order.returnUntil!, lang))}
              </span>
              {!returning && (
                <button type="button" className="s-cta" onClick={() => setReturning(true)}>
                  {t.returnOrExchange}
                </button>
              )}
            </>
          )}
        </div>
      ) : null}

      {returning && onReturn && (
        <ReturnForm
          order={order}
          onCancel={() => setReturning(false)}
          onSubmit={async (input) => {
            await onReturn(input)
            setReturning(false)
            setMsg({ ok: true, text: t.requestSent })
          }}
        />
      )}

      {order.returns.length > 0 && (
        <div>
          <p className="s-lab" style={{ margin: '0 0 8px' }}>
            {t.yourRequests}
          </p>
          {order.returns.map((r) => (
            <div key={r.number} className="s-line" style={{ gridTemplateColumns: '1fr auto' }}>
              <div className="s-linemeta">
                <span>
                  {r.type === 'EXCHANGE' ? t.typeExchange : t.typeReturn} · <bdi dir="ltr">{r.number}</bdi>
                </span>
                {r.items.map((i) => (
                  <span key={i.orderItemId} className="s-lab">
                    <bdi>{i.name}</bdi> · {i.size} × {i.qty}
                    {i.exchangeSize ? ` → ${i.exchangeSize}` : ''}
                  </span>
                ))}
                <span className="s-fhint">
                  {t.reasons[r.reason]} · {formatDate(r.createdAt, lang)}
                </span>
              </div>
              <div className="s-lineend">
                <StatusPill status={r.status === 'COMPLETED' ? 'DELIVERED' : r.status === 'REJECTED' ? 'CANCELLED' : r.status} label={t.returnStatus[r.status]} />
                {r.refundAmount != null && (
                  <span className="s-price">
                    {t.refund}: {formatPrice(r.refundAmount, lang)}
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <div>
        {order.items.map((i) => (
          <LineItem key={`${i.slug}|${i.size}`} slug={i.slug} size={i.size} qty={i.qty} name={i.name} image={i.image} lineTotal={i.lineTotal} />
        ))}
      </div>

      <dl className="s-totals">
        <dt>{t.subtotal}</dt>
        <dd>{formatPrice(order.subtotal, lang)}</dd>
        {order.discount > 0 && (
          <>
            <dt>
              {t.discount} {order.couponCode && <bdi dir="ltr">({order.couponCode})</bdi>}
            </dt>
            <dd>−{formatPrice(order.discount, lang)}</dd>
          </>
        )}
        <dt>{t.shippingFee}</dt>
        <dd>{order.shipping === 0 ? t.free : formatPrice(order.shipping, lang)}</dd>
        <dt className="s-grand">{t.total}</dt>
        <dd className="s-grand">{formatPrice(order.total, lang)}</dd>
      </dl>

      <div className="s-card-box">
        <dl className="s-kv">
          <dt>{t.placedOn}</dt>
          <dd>{formatDate(order.createdAt, lang)}</dd>
          <dt>{t.deliverTo}</dt>
          <dd>
            {order.name} · <bdi dir="ltr">{order.phone}</bdi>
            <br />
            {order.street}
            {order.building ? `, ${order.building}` : ''}, {order.city}, {zone ? l(zone.name) : order.governorate}
          </dd>
          <dt>{t.payment}</dt>
          <dd>{order.paymentMethod === 'COD' ? t.cod : order.paymentMethod}</dd>
        </dl>
      </div>
    </div>
  )
}

function ReturnForm({ order, onSubmit, onCancel }: { order: Order; onSubmit: (r: ReturnInput) => Promise<void>; onCancel: () => void }) {
  const { t } = useI18n()
  const { data: products = [] } = useProducts()
  const [type, setType] = useState<ReturnType>('EXCHANGE')
  const [reason, setReason] = useState<ReturnReason>('SIZE')
  const [note, setNote] = useState('')
  const [picked, setPicked] = useState<Record<number, { qty: number; size: string } | undefined>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const items = order.items.filter((i) => i.returnableQty > 0)
  const sizesFor = (slug: string) => products.find((p) => p.slug === slug)?.variants.map((v) => v.size) ?? []
  const pickType = (k: ReturnType) => {
    setType(k)
    if (!REASONS[k].includes(reason)) setReason(REASONS[k][0]!)
  }
  const reasonHint =
    type === 'RETURN' ? t.refundOnlyOurMistake : OUR_MISTAKE.includes(reason) ? t.exchangeFreeShipping : t.exchangeShippingFee

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    const chosen = items.filter((i) => picked[i.id])
    if (!chosen.length) return setError(t.pickAtLeastOne)
    if (type === 'EXCHANGE' && chosen.some((i) => !picked[i.id]!.size)) return setError(t.pickExchangeSize)
    setBusy(true)
    try {
      await onSubmit({
        type,
        reason,
        note: note.trim() || undefined,
        items: chosen.map((i) => ({ itemId: i.id, qty: picked[i.id]!.qty, ...(type === 'EXCHANGE' ? { exchangeSize: picked[i.id]!.size } : {}) })),
      })
    } catch (err) {
      const code = err instanceof ApiError ? err.code : ''
      setError(t.returnError[code] ?? errorMessage(err, t))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="s-form s-card-box" onSubmit={submit} noValidate>
      <h2 className="s-h">{t.returnTitle}</h2>
      <fieldset className="s-fieldset">
        <legend className="s-lab">{t.whatToDo}</legend>
        {(['EXCHANGE', 'RETURN'] as const).map((k) => (
          <label key={k} className="s-radio">
            <input type="radio" name="rtype" checked={type === k} onChange={() => pickType(k)} />
            <span style={{ margin: 0 }}>
              <b>{k === 'EXCHANGE' ? t.typeExchange : t.typeReturn}</b>
            </span>
          </label>
        ))}
      </fieldset>

      <fieldset className="s-fieldset">
        <legend className="s-lab">{t.whichItems}</legend>
        {items.map((i) => {
          const sel = picked[i.id]
          const sizes = sizesFor(i.slug)
          return (
            <div key={i.id} className="s-radio" style={{ flexWrap: 'wrap', alignItems: 'center' }}>
              <input
                type="checkbox"
                checked={!!sel}
                onChange={(e) => setPicked((p) => ({ ...p, [i.id]: e.target.checked ? { qty: 1, size: '' } : undefined }))}
                aria-label={i.name}
              />
              <span style={{ margin: 0, flex: 1, minWidth: 140 }}>
                <b>
                  <bdi>{i.name}</bdi>
                </b>
                <span>
                  {t.sizeLabel(i.size)} · {t.qtyLabel(i.qty)}
                </span>
              </span>
              {sel && (
                <span style={{ display: 'flex', gap: 8, margin: 0 }}>
                  {i.returnableQty > 1 && (
                    <select className="s-input" style={{ width: 80, minHeight: 40, padding: '6px 10px' }} value={sel.qty} aria-label={t.quantity}
                      onChange={(e) => setPicked((p) => ({ ...p, [i.id]: { ...sel, qty: Number(e.target.value) } }))}>
                      {Array.from({ length: i.returnableQty }, (_, n) => n + 1).map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                  )}
                  {type === 'EXCHANGE' && (
                    <select className="s-input" style={{ width: 110, minHeight: 40, padding: '6px 10px' }} value={sel.size} aria-label={t.exchangeFor}
                      onChange={(e) => setPicked((p) => ({ ...p, [i.id]: { ...sel, size: e.target.value } }))}>
                      <option value="">{t.exchangeFor}</option>
                      {sizes.map((z) => (
                        <option key={z} value={z}>
                          {z}
                        </option>
                      ))}
                    </select>
                  )}
                </span>
              )}
            </div>
          )
        })}
      </fieldset>

      <div className="s-field">
        <label htmlFor="rreason">
          <span className="s-lab">{t.reasonLabel}</span>
        </label>
        <select id="rreason" className="s-input" value={reason} aria-describedby="rreason-hint" onChange={(e) => setReason(e.target.value as ReturnReason)}>
          {REASONS[type].map((r) => (
            <option key={r} value={r}>
              {t.reasons[r]}
            </option>
          ))}
        </select>
        <p className="s-fhint" id="rreason-hint">
          {reasonHint}
        </p>
      </div>
      <TextArea label={t.notesOptional} value={note} onChange={(e) => setNote(e.target.value)} rows={3} maxLength={1000} />
      {error && (
        <p className="s-alert" role="alert">
          {error}
        </p>
      )}
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <button type="submit" className="s-add" disabled={busy}>
          {busy ? <span className="s-spinner" aria-hidden="true" /> : t.sendRequest}
        </button>
        <button type="button" className="s-cta" style={{ height: 52 }} onClick={onCancel}>
          {t.cancel}
        </button>
      </div>
    </form>
  )
}
