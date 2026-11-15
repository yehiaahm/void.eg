import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, type Order } from '../api'
import { Field } from '../components/Field'
import { OrderView } from '../components/OrderView'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'
import { errorMessage, isEgMobile } from '../lib/errors'
import '../styles/forms.css'

/**
 * Guest order tracking. The private `t` token from the e-mailed link opens the full order and allows cancelling or
 * returning; the order number + phone only opens a masked, read-only status view.
 */
export function TrackPage() {
  const { t } = useI18n()
  useDocumentTitle(`${t.trackOrder} — VOID`)
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

  // Arriving from the e-mail link: open the order straight away.
  useEffect(() => {
    const n = params.get('order')
    if (auto.current || !n || !linkToken) return
    auto.current = true
    setBusy(true)
    api
      .track(n.trim(), { token: linkToken })
      .then((o) => {
        setOrder(o)
        setToken(linkToken)
      })
      .catch((err) => setError(errorMessage(err, t)))
      .finally(() => setBusy(false))
  }, [params, linkToken, t])

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

  return (
    <main className="s-main s-page">
      <div className="s-narrow" style={{ maxWidth: 560 }}>
        <h1 className="s-title">{t.trackOrder}</h1>
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
          <div style={{ marginTop: 40 }} aria-live="polite">
            {!token && (
              <p className="s-note" role="note">
                {t.trackLimitedHint}
              </p>
            )}
            <OrderView
              order={order}
              onCancel={token ? async () => setOrder(await api.guestCancel(order.number, token)) : undefined}
              onReturn={token ? async (r) => setOrder(await api.guestReturn(order.number, token, r)) : undefined}
            />
          </div>
        )}
      </div>
    </main>
  )
}
