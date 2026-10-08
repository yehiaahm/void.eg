import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, type Order } from '../api'
import { Field } from '../components/Field'
import { OrderView } from '../components/OrderView'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'
import { errorMessage, isEgMobile } from '../lib/errors'
import '../styles/forms.css'

export function TrackPage() {
  const { t } = useI18n()
  useDocumentTitle(`${t.trackOrder} — VOID`)
  const [params] = useSearchParams()
  const [number, setNumber] = useState(params.get('order') ?? '')
  const [phone, setPhone] = useState('')
  const [order, setOrder] = useState<Order | null>(null)
  const [found, setFound] = useState<{ number: string; phone: string } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!number.trim()) return setError(t.required)
    if (!isEgMobile(phone)) return setError(t.invalidPhone)
    setBusy(true)
    try {
      setOrder(await api.track(number.trim(), phone))
      setFound({ number: number.trim(), phone })
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
            <OrderView
              order={order}
              onCancel={found ? async () => setOrder(await api.guestCancel(found.number, found.phone)) : undefined}
              onReturn={found ? async (r) => setOrder(await api.guestReturn(found.number, found.phone, r)) : undefined}
            />
          </div>
        )}
      </div>
    </main>
  )
}
