import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, api, useSettings } from '../api'
import { Field, TextArea } from '../components/Field'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'
import { EMAIL_RE, errorMessage, normalizePhone } from '../lib/errors'
import '../styles/forms.css'
import './ContactPage.css'

interface Form {
  name: string
  email: string
  phone: string
  message: string
}
const EMPTY: Form = { name: '', email: '', phone: '', message: '' }

/** Keeps an email reading left-to-right inside Arabic copy. */
const ltr = (s: string) => `⁦${s}⁩`

/** Any phone number (Egyptian or international): 7–15 digits, optional leading +. */
const isPhone = (raw: string) => /^\+?\d{7,15}$/.test(normalizePhone(raw).replace(/^00/, '+'))

/** Footer → Contact: the store's email + a message form (lands in Admin → Messages). */
export function ContactPage() {
  const { lang, t } = useI18n()
  const { data: settings } = useSettings()
  useDocumentTitle(t.contactTitle)

  const [f, setF] = useState<Form>(EMPTY)
  const [errors, setErrors] = useState<Partial<Record<keyof Form, string>>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState<{ name: string; email: string } | null>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const doneRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (sent) doneRef.current?.focus()
  }, [sent])

  const storeEmail = settings?.contactEmail

  const set = (k: keyof Form) => (e: { target: { value: string } }) => {
    setF((x) => ({ ...x, [k]: e.target.value }))
    if (errors[k]) setErrors((x) => ({ ...x, [k]: undefined }))
  }

  const validate = () => {
    const e: Partial<Record<keyof Form, string>> = {}
    if (!f.name.trim()) e.name = t.required
    if (!f.email.trim()) e.email = t.required
    else if (!EMAIL_RE.test(f.email.trim())) e.email = t.invalidEmail
    if (f.phone.trim() && !isPhone(f.phone)) e.phone = t.invalidPhoneLoose
    if (!f.message.trim()) e.message = t.required
    setErrors(e)
    const first = Object.keys(e)[0]
    if (first) formRef.current?.querySelector<HTMLElement>(`[name="${first}"]`)?.focus()
    return !first
  }

  const submit = async (ev: FormEvent) => {
    ev.preventDefault()
    if (busy) return
    setFormError(null)
    if (!validate()) return
    setBusy(true)
    try {
      await api.sendContact({
        name: f.name.trim(),
        email: f.email.trim(),
        phone: f.phone.trim() || undefined,
        message: f.message.trim(),
        lang,
      })
      setSent({ name: f.name.trim().split(/\s+/)[0], email: f.email.trim() })
      setF(EMPTY)
    } catch (e) {
      if (e instanceof ApiError && e.fields?.email) setErrors((x) => ({ ...x, email: t.invalidEmail }))
      else if (e instanceof ApiError && e.fields?.phone) setErrors((x) => ({ ...x, phone: t.invalidPhoneLoose }))
      else setFormError(errorMessage(e, t))
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="s-main s-page s-contact">
      <h1 className="s-sr">{t.contact}</h1>
      <div className="s-contact-copy">
        <p>{t.contactIntro}</p>
        {storeEmail ? (
          <p>
            {t.contactAtBefore}{' '}
            <a href={`mailto:${storeEmail}`} dir="ltr">
              {storeEmail}
            </a>
            {t.contactAtAfter}
          </p>
        ) : (
          <p>{t.contactNoEmail}</p>
        )}
      </div>

      {sent ? (
        <div className="s-contact-done">
          <p tabIndex={-1} ref={doneRef} role="status">
            {t.contactSent(sent.name, ltr(sent.email))}
          </p>
          <button type="button" className="s-linkbtn" onClick={() => setSent(null)}>
            {t.sendAnother}
          </button>
        </div>
      ) : (
        <form className="s-form s-contact-form" ref={formRef} onSubmit={submit} noValidate>
          <Field label={t.yourName} name="name" autoComplete="name" value={f.name} onChange={set('name')} error={errors.name} maxLength={120} />
          <Field
            label={t.email}
            name="email"
            type="email"
            autoComplete="email"
            inputMode="email"
            value={f.email}
            onChange={set('email')}
            error={errors.email}
            dir="ltr"
            maxLength={190}
          />
          <Field
            label={t.phoneNumber}
            name="phone"
            type="tel"
            autoComplete="tel"
            inputMode="tel"
            value={f.phone}
            onChange={set('phone')}
            error={errors.phone}
            aside={<span className="s-fhint">{t.optional}</span>}
            dir="ltr"
            maxLength={32}
          />
          <TextArea label={t.message} name="message" value={f.message} onChange={set('message')} error={errors.message} rows={8} maxLength={2000} />
          {formError && (
            <p className="s-alert" role="alert">
              {formError}
            </p>
          )}
          <button type="submit" className="s-cta s-full" disabled={busy}>
            {busy ? (
              <>
                <span className="s-spinner" aria-hidden="true" /> {t.sending}
              </>
            ) : (
              t.send
            )}
          </button>
        </form>
      )}
    </main>
  )
}
