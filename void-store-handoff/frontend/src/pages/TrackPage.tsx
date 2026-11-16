import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, type Order } from '../api'
import { Field } from '../components/Field'
import { OrderProgress } from '../components/MyOrders'
import { OrderView, StatusPill, formatDate } from '../components/OrderView'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'
import { errorMessage, isEgMobile } from '../lib/errors'
import { formatPrice } from '../lib/format'
import { useAuth } from '../store/auth'
import { useMyOrders } from '../store/myOrders'
import { useToast } from '../store/toast'
import '../styles/forms.css'

/**
 * "My orders" + guest order tracking.
 *
 * The orders this device remembers (and the signed-in customer's own) are listed first, each with its live status.
 * The private `t` token from the e-mailed link opens the full order and allows cancelling or returning — and the
 * device remembers it from then on; the order number + phone only opens a masked, read-only status view.
 */
export function TrackPage() {
  const { lang, t } = useI18n()
  useDocumentTitle(`${t.trackOrder} — VOID`)
  const qc = useQueryClient()
  const mine = useMyOrders()
  const { user } = useAuth()
  const flash = useToast()
  const [adding, setAdding] = useState(false)
  const [params] = useSearchParams()
  const [number, setNumber] = useState(params.get('order') ?? '')
  const [phone, setPhone] = useState('')
  const [order, setOrder] = useState<Order | null>(null)
  /** Present only when the order was opened with the private token. */
  const [token, setToken] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const linkToken = params.get('t')
  const auto = useRef(false)
  const detail = useRef<HTMLDivElement>(null)
  const scrollTo = useRef(false)

  /** Open an order with its private token (the e-mailed link, or a device-remembered order). */
  const openWithToken = useCallback(
    async (n: string, tok: string) => {
      setError(null)
      setBusy(true)
      try {
        const o = await api.track(n.trim(), { token: tok })
        setOrder(o)
        setToken(tok)
        mine.remember(o.number, tok) // opened from the e-mail on a new device? it's remembered from now on
        scrollTo.current = true
      } catch (err) {
        setError(errorMessage(err, t))
      } finally {
        setBusy(false)
      }
    },
    [mine, t],
  )

  // Arriving from the e-mail link (or the home strip): open the order straight away.
  useEffect(() => {
    const n = params.get('order')
    if (auto.current || !n || !linkToken) return
    auto.current = true
    void openWithToken(n, linkToken)
  }, [params, linkToken, openWithToken])

  // Bring the opened order into view (it sits below the lookup form).
  useEffect(() => {
    if (order && scrollTo.current) {
      scrollTo.current = false
      detail.current?.scrollIntoView({ block: 'start' })
    }
  }, [order])

  // Opened with the private link while signed in, and it isn't in the account yet: offer to add it.
  const canAdd = !!user && !!token && !!order && !mine.accountNumbers.includes(order.number)
  const addToAccount = async () => {
    if (!order) return
    setAdding(true)
    try {
      await mine.claim([order.number])
      flash(t.claimDone)
    } catch (err) {
      setError(errorMessage(err, t))
    } finally {
      setAdding(false)
    }
  }

  const refreshList = () => qc.invalidateQueries({ queryKey: ['device-orders'] })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!number.trim()) return setError(t.required)
    if (!isEgMobile(phone)) return setError(t.invalidPhone)
    setBusy(true)
    try {
      setOrder(await api.track(number.trim(), { phone }))
      setToken(null)
    } catch (err) {
      setOrder(null)
      setError(errorMessage(err, t))
    } finally {
      setBusy(false)
    }
  }

  const hasOrders = mine.orders.length > 0
  return (
    <main className="s-main s-page">
      <div className="s-narrow" style={{ maxWidth: 560 }}>
        <h1 className="s-title">{hasOrders ? t.yourOrders : t.trackOrder}</h1>

        {hasOrders && (
          <section className="s-myorders" aria-label={t.yourOrders}>
            {mine.orders.map((o) => {
              const meta = (
                <>
                  <span dir="ltr" style={{ fontFamily: 'var(--f-mono)', alignSelf: 'flex-start' }}>
                    {o.number}
                  </span>
                  <span className="s-lab">
                    {formatDate(o.createdAt, lang)} · {t.items(o.itemCount)}
                  </span>
                </>
              )
              return (
                <div key={o.number} className="s-myorder" aria-current={order?.number === o.number}>
                  {o.token ? (
                    <button type="button" className="s-myorder-meta" onClick={() => openWithToken(o.number, o.token!)} disabled={busy}>
                      {meta}
                    </button>
                  ) : (
                    <Link className="s-myorder-meta" to={`/${lang}/account/orders/${o.number}`}>
                      {meta}
                    </Link>
                  )}
                  <span className="s-myorder-end">
                    <span className="s-price">{formatPrice(o.total, lang)}</span>
                    <StatusPill status={o.status} />
                  </span>
                  <div className="s-myorder-foot">
                    <OrderProgress status={o.status} />
                    {o.token && (
                      <button type="button" className="s-linkbtn" style={{ fontSize: 12 }} onClick={() => mine.forget(o.number)}>
                        {t.forgetOrder}
                      </button>
                    )}
                  </div>
                </div>
              )
            })}
            <p className="s-fhint" style={{ marginTop: 14 }}>
              {t.deviceOrdersHint}
            </p>
          </section>
        )}

        {hasOrders ? <h2 className="s-h">{t.findAnotherOrder}</h2> : null}
        <p className="s-sub">{t.trackIntro}</p>
        <form className="s-form" onSubmit={submit} noValidate>
          <div className="s-grid2">
            <Field label={t.orderNumber} value={number} onChange={(e) => setNumber(e.target.value.toUpperCase())} placeholder={t.orderNumberPh} dir="ltr" autoComplete="off" />
            <Field label={t.phone} type="tel" inputMode="tel" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" dir="ltr" />
          </div>
          {error && (
            <p className="s-alert" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="s-add" disabled={busy}>
            {busy ? <span className="s-spinner" aria-hidden="true" /> : t.find}
          </button>
        </form>
        {order && (
          <div ref={detail} style={{ marginTop: 40, scrollMarginTop: 90 }} aria-live="polite">
            {!token && (
              <p className="s-note" role="note">
                {t.trackLimitedHint}
              </p>
            )}
            {canAdd && (
              <div className="s-card-box" style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
                <span className="s-sub" style={{ margin: 0 }}>
                  {t.claimHint}
                </span>
                <button type="button" className="s-cta" onClick={addToAccount} disabled={adding}>
                  {t.claimAdd}
                </button>
              </div>
            )}
            <OrderView
              order={order}
              onCancel={
                token
                  ? async () => {
                      setOrder(await api.guestCancel(order.number, token))
                      refreshList()
                    }
                  : undefined
              }
              onReturn={
                token
                  ? async (r) => {
                      setOrder(await api.guestReturn(order.number, token, r))
                      refreshList()
                    }
                  : undefined
              }
            />
          </div>
        )}
      </div>
    </main>
  )
}
