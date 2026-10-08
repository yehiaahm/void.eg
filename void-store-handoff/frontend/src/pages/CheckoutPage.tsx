import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError, api, useSettings, useZones, type Address, type StoreSettings } from '../api'
import { Field, Select, TextArea } from '../components/Field'
import { LineItem } from '../components/LineItem'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useQuote } from '../hooks/useQuote'
import { useI18n } from '../i18n'
import { EMAIL_RE, errorMessage, isEgMobile } from '../lib/errors'
import { formatPrice } from '../lib/format'
import { useAuth } from '../store/auth'
import { useBag } from '../store/bag'
import '../styles/forms.css'
import './CheckoutPage.css'

interface Form {
  email: string
  name: string
  phone: string
  governorate: string
  city: string
  street: string
  building: string
  notes: string
  note: string
}
const EMPTY: Form = { email: '', name: '', phone: '', governorate: '', city: '', street: '', building: '', notes: '', note: '' }

export function CheckoutPage() {
  const { lang, t, l } = useI18n()
  useDocumentTitle(`${t.checkout} — VOID`)
  const bag = useBag()
  const { user } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data: zones = [], isLoading: zonesLoading } = useZones()
  const { data: settings } = useSettings()
  const { data: saved = [] } = useQuery({ queryKey: ['addresses', user?.id], queryFn: api.addresses, enabled: !!user })

  const [f, setF] = useState<Form>(EMPTY)
  const [errors, setErrors] = useState<Partial<Record<keyof Form, string>>>({})
  const [couponInput, setCouponInput] = useState('')
  const [coupon, setCoupon] = useState('')
  const [addressId, setAddressId] = useState<number | 'new'>('new')
  const [saveAddress, setSaveAddress] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const { data: quote, isFetching } = useQuote(bag.lines, { governorate: f.governorate, coupon, email: f.email })

  // Prefill from the account.
  useEffect(() => {
    if (!user) return
    setF((x) => ({ ...x, email: x.email || user.email, name: x.name || user.name, phone: x.phone || user.phone || '' }))
  }, [user])

  const applyAddress = (a: Address) =>
    setF((x) => ({ ...x, name: a.name, phone: a.phone, governorate: a.governorate, city: a.city, street: a.street, building: a.building, notes: a.notes }))

  useEffect(() => {
    const def = saved.find((a) => a.isDefault) ?? saved[0]
    if (def && addressId === 'new' && !f.street) {
      setAddressId(def.id)
      applyAddress(def)
    }
    // run once when saved addresses load
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [saved])

  const set = (k: keyof Form) => (e: { target: { value: string } }) => {
    setF((x) => ({ ...x, [k]: e.target.value }))
    if (errors[k]) setErrors((x) => ({ ...x, [k]: undefined }))
  }

  const zoneKnown = useMemo(() => zones.some((z) => z.code === f.governorate), [zones, f.governorate])

  if (bag.lines.length === 0 && !submitting) return <Navigate to={`/${lang}`} replace />

  const validate = () => {
    const e: Partial<Record<keyof Form, string>> = {}
    if (!EMAIL_RE.test(f.email.trim())) e.email = t.invalidEmail
    if (!f.name.trim()) e.name = t.required
    if (!isEgMobile(f.phone)) e.phone = t.invalidPhone
    if (!zoneKnown) e.governorate = t.required
    if (!f.city.trim()) e.city = t.required
    if (!f.street.trim()) e.street = t.required
    setErrors(e)
    const first = Object.keys(e)[0]
    if (first) document.querySelector<HTMLElement>(`[name="${first}"]`)?.focus()
    return !first
  }

  const blocked = quote?.items.some((i) => i.issue) ?? false

  const submit = async (ev: FormEvent) => {
    ev.preventDefault()
    setFormError(null)
    if (!validate() || blocked || submitting) return
    setSubmitting(true)
    try {
      const res = await api.placeOrder({
        items: bag.lines.map((x) => ({ slug: x.slug, size: x.size, qty: x.qty })),
        contact: { email: f.email.trim(), name: f.name.trim(), phone: f.phone },
        address: { governorate: f.governorate, city: f.city.trim(), street: f.street.trim(), building: f.building.trim(), notes: f.notes.trim() },
        note: f.note.trim(),
        couponCode: coupon || undefined,
        paymentMethod: 'COD',
        lang,
        saveAddress: !!user && addressId === 'new' && saveAddress,
      })
      qc.invalidateQueries({ queryKey: ['products'] })
      qc.invalidateQueries({ queryKey: ['product'] })
      navigate(`/${lang}/order/${res.number}`, { replace: true, state: { email: f.email.trim(), total: res.total } })
      bag.clear()
    } catch (e) {
      setSubmitting(false)
      if (e instanceof ApiError && e.code === 'stock_changed') qc.invalidateQueries({ queryKey: ['quote'] })
      if (e instanceof ApiError && e.code.startsWith('coupon_')) setCoupon('')
      // The owner closed the store meanwhile: switch to the waitlist page (the bag stays saved).
      if (e instanceof ApiError && e.code === 'store_closed') qc.setQueryData<StoreSettings>(['settings'], (s) => s && { ...s, storeClosed: true })
      setFormError(errorMessage(e, t))
    }
  }

  const couponError = quote?.couponError ? (t.couponError[quote.couponError] ?? t.couponError.invalid) : null
  const noZones = !zonesLoading && zones.length === 0

  return (
    <main className="s-main s-page">
      <div className="s-wide">
        <h1 className="s-title">{t.checkout}</h1>
        {!user && (
          <p className="s-sub">
            <Link className="s-linkbtn" to={`/${lang}/account?next=checkout`}>
              {t.signInFaster}
            </Link>
          </p>
        )}

        <div className="s-co">
          <form className="s-form s-co-form" onSubmit={submit} noValidate>
            <fieldset className="s-fieldset">
              <legend className="s-lab">{t.contactInfo}</legend>
              <Field label={t.email} name="email" type="email" autoComplete="email" inputMode="email" value={f.email} onChange={set('email')} error={errors.email} dir="ltr" />
              <div className="s-grid2">
                <Field label={t.fullName} name="name" autoComplete="name" value={f.name} onChange={set('name')} error={errors.name} />
                <Field label={t.phone} name="phone" type="tel" autoComplete="tel" inputMode="tel" value={f.phone} onChange={set('phone')} error={errors.phone} hint={t.phoneHint} dir="ltr" />
              </div>
            </fieldset>

            <fieldset className="s-fieldset">
              <legend className="s-lab">{t.delivery}</legend>
              {noZones && <p className="s-alert">{t.noZones}</p>}
              {saved.length > 0 && (
                <div className="s-stack" role="radiogroup" aria-label={t.savedAddresses}>
                  {saved.map((a) => (
                    <label className="s-radio" key={a.id}>
                      <input
                        type="radio"
                        name="addr"
                        checked={addressId === a.id}
                        onChange={() => {
                          setAddressId(a.id)
                          applyAddress(a)
                        }}
                      />
                      <span style={{ margin: 0 }}>
                        <b>{a.name}</b>
                        <span>
                          {a.street}
                          {a.building ? `, ${a.building}` : ''}, {a.city} · <bdi dir="ltr">{a.phone}</bdi>
                        </span>
                      </span>
                    </label>
                  ))}
                  <label className="s-radio">
                    <input type="radio" name="addr" checked={addressId === 'new'} onChange={() => setAddressId('new')} />
                    <span style={{ margin: 0 }}>
                      <b>{t.addAddress}</b>
                    </span>
                  </label>
                </div>
              )}
              <Select label={t.governorate} name="governorate" value={zoneKnown ? f.governorate : ''} onChange={set('governorate')} error={errors.governorate} disabled={noZones}>
                <option value="">{t.chooseGovernorate}</option>
                {zones.map((z) => (
                  <option key={z.code} value={z.code}>
                    {l(z.name)} — {formatPrice(z.fee, lang)}
                  </option>
                ))}
              </Select>
              <div className="s-grid2">
                <Field label={t.city} name="city" autoComplete="address-level2" value={f.city} onChange={set('city')} error={errors.city} />
                <Field label={t.building} name="building" value={f.building} onChange={set('building')} aside={<span className="s-fhint">{t.optional}</span>} />
              </div>
              <Field label={t.street} name="street" autoComplete="street-address" value={f.street} onChange={set('street')} error={errors.street} />
              <Field label={t.addressNotes} name="notes" value={f.notes} onChange={set('notes')} aside={<span className="s-fhint">{t.optional}</span>} />
              {user && addressId === 'new' && (
                <label className="s-check">
                  <input type="checkbox" checked={saveAddress} onChange={(e) => setSaveAddress(e.target.checked)} />
                  {t.saveAddress}
                </label>
              )}
            </fieldset>

            <fieldset className="s-fieldset">
              <legend className="s-lab">{t.payment}</legend>
              {settings?.codEnabled !== false && (
                <label className="s-radio">
                  <input type="radio" name="pay" defaultChecked />
                  <span style={{ margin: 0 }}>
                    <b>{t.cod}</b>
                    <span>{t.codHint}</span>
                  </span>
                </label>
              )}
            </fieldset>

            <TextArea label={t.orderNote} name="note" value={f.note} onChange={set('note')} maxLength={1000} rows={3} />

            {formError && (
              <p className="s-alert" role="alert">
                {formError}
              </p>
            )}
            <button type="submit" className="s-add s-full" disabled={submitting || blocked || noZones}>
              {submitting ? (
                <>
                  <span className="s-spinner" aria-hidden="true" /> {t.placing}
                </>
              ) : (
                <>
                  {t.placeOrder}
                  {quote?.total != null && ` · ${formatPrice(quote.total, lang)}`}
                </>
              )}
            </button>
          </form>

          <aside className="s-co-sum" aria-label={t.orderSummary}>
            <details className="s-co-details" open>
              <summary>
                <span className="s-lab">{t.orderSummary}</span>
                <span className="s-price">{quote ? formatPrice(quote.total ?? quote.subtotal - quote.discount, lang) : ''}</span>
              </summary>
              <div aria-busy={isFetching}>
                {bag.lines.map((line) => {
                  const q = quote?.items.find((x) => x.slug === line.slug && x.size === line.size)
                  return (
                    <LineItem key={`${line.slug}|${line.size}`} slug={line.slug} size={line.size} qty={line.qty} name={q?.name}
                      image={q?.image} lineTotal={q?.lineTotal} issue={q?.issue} available={q?.available} />
                  )
                })}
                <form
                  className="s-coupon"
                  onSubmit={(e) => {
                    e.preventDefault()
                    setCoupon(couponInput.trim())
                  }}
                >
                  {quote?.couponCode ? (
                    <p className="s-note" style={{ flex: 1, display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                      <span dir="ltr">{quote.couponCode}</span>
                      <button type="button" className="s-linkbtn" onClick={() => { setCoupon(''); setCouponInput('') }}>
                        {t.removeCode}
                      </button>
                    </p>
                  ) : (
                    <>
                      <div style={{ flex: 1 }}>
                        <Field label={t.couponCode} value={couponInput} onChange={(e) => setCouponInput(e.target.value.toUpperCase())} error={coupon ? couponError : null} dir="ltr" autoComplete="off" />
                      </div>
                      <button type="submit" className="s-cta" style={{ height: 48, alignSelf: 'flex-start', marginTop: 23 }} disabled={!couponInput.trim()}>
                        {t.apply}
                      </button>
                    </>
                  )}
                </form>
                {quote && (
                  <dl className="s-totals">
                    <dt>{t.subtotal}</dt>
                    <dd>{formatPrice(quote.subtotal, lang)}</dd>
                    {quote.discount > 0 && (
                      <>
                        <dt>{t.discount}</dt>
                        <dd>−{formatPrice(quote.discount, lang)}</dd>
                      </>
                    )}
                    <dt>{t.shippingFee}</dt>
                    <dd>{quote.shipping == null ? '—' : quote.shipping === 0 ? t.free : formatPrice(quote.shipping, lang)}</dd>
                    <dt className="s-grand">{t.total}</dt>
                    <dd className="s-grand">{quote.total == null ? '—' : formatPrice(quote.total, lang)}</dd>
                  </dl>
                )}
                {blocked && <p className="s-ferr" style={{ marginTop: 12 }}>{t.fixBag}</p>}
              </div>
            </details>
          </aside>
        </div>
      </div>
    </main>
  )
}
