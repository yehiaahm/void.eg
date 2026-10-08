import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api, useSettings } from '../api'
import { Field, TextArea } from '../components/Field'
import type { BlackHole } from '../components/hero/blackHole'
import { InstagramIcon, TikTokIcon } from '../components/icons'
import { LangSwitch } from '../components/LangSwitch'
import { VoidCheck, VoidStage } from '../components/VoidStage'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'
import { EMAIL_RE, errorMessage, isEgMobile, normalizePhone } from '../lib/errors'
import '../components/Header.css'
import '../components/Footer.css'
import '../styles/forms.css'
import './ClosedPage.css'

interface Form {
  name: string
  phone: string
  email: string
  notes: string
}
/** form → leaving (the form is pulled into the hole) → sent (check mark + thanks). */
type Phase = 'form' | 'leaving' | 'sent'

const reducedMotion = () => {
  try {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches
  } catch {
    return false
  }
}
const wait = (ms: number) => new Promise((r) => setTimeout(r, ms))
/** Keeps a phone number / email reading left-to-right inside Arabic copy. */
const ltr = (s: string) => `⁦${s}⁩`

/**
 * Shown instead of the shop while the owner has the store closed (Admin → Dashboard).
 * Visitors leave their name + mobile (email and notes optional); on submit the form is
 * swallowed by the black hole and a check mark lights up inside the event horizon.
 */
export function ClosedPage() {
  const { lang, t } = useI18n()
  const { data: settings } = useSettings()
  useDocumentTitle(t.closedTitle)

  const [f, setF] = useState<Form>({ name: '', phone: '', email: '', notes: '' })
  const [errors, setErrors] = useState<Partial<Record<keyof Form, string>>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [phase, setPhase] = useState<Phase>('form')
  const [sent, setSent] = useState<{ name: string; phone: string; email: string } | null>(null)

  const engine = useRef<BlackHole | null>(null)
  const core = useRef<HTMLSpanElement>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const doneRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    if (phase === 'sent') doneRef.current?.focus({ preventScroll: true })
  }, [phase])

  const message = settings?.closedMessage?.[lang]?.trim() || t.closedBody
  const social = [
    { label: 'Instagram', href: settings?.instagramUrl, Icon: InstagramIcon },
    { label: 'TikTok', href: settings?.tiktokUrl, Icon: TikTokIcon },
  ].filter((s) => s.href)

  const set = (k: keyof Form) => (e: { target: { value: string } }) => {
    setF((x) => ({ ...x, [k]: e.target.value }))
    if (errors[k]) setErrors((x) => ({ ...x, [k]: undefined }))
  }

  const validate = () => {
    const e: Partial<Record<keyof Form, string>> = {}
    if (!f.name.trim()) e.name = t.required
    if (!isEgMobile(f.phone)) e.phone = t.invalidPhone
    if (f.email.trim() && !EMAIL_RE.test(f.email.trim())) e.email = t.invalidEmail
    setErrors(e)
    const first = Object.keys(e)[0]
    if (first) formRef.current?.querySelector<HTMLElement>(`[name="${first}"]`)?.focus()
    return !first
  }

  /** The form spirals into the event horizon while the particles surge, then the thanks arrive. */
  const enterTheVoid = async () => {
    const form = formRef.current
    const target = core.current
    if (!form || !target || reducedMotion() || typeof form.animate !== 'function') {
      setPhase('sent')
      return
    }
    setPhase('leaving')
    const a = form.getBoundingClientRect()
    const b = target.getBoundingClientRect()
    const dx = b.left + b.width / 2 - (a.left + a.width / 2)
    const dy = b.top + b.height / 2 - (a.top + a.height / 2)
    const spin = lang === 'ar' ? -1 : 1
    window.scrollTo({ top: 0, behavior: 'smooth' })
    const anim = form.animate(
      [
        { transform: 'none', opacity: 1, filter: 'blur(0px)' },
        {
          transform: `translate(${dx * 0.08 + spin * a.width * 0.12}px, ${dy * 0.32}px) scale(0.7) rotate(${-8 * spin}deg)`,
          opacity: 0.95,
          filter: 'blur(0px)',
          offset: 0.4,
        },
        { transform: `translate(${dx}px, ${dy}px) scale(0.02) rotate(${50 * spin}deg)`, opacity: 0, filter: 'blur(8px)' },
      ],
      { duration: 1000, easing: 'cubic-bezier(0.5, 0, 0.75, 0.3)', fill: 'forwards' },
    )
    setTimeout(() => engine.current?.pulse(), 600)
    // Animations pause in background tabs — never leave the visitor without the confirmation.
    await Promise.race([anim.finished.catch(() => undefined), wait(1400)])
    setPhase('sent')
  }

  const submit = async (ev: FormEvent) => {
    ev.preventDefault()
    if (busy || phase !== 'form') return
    setFormError(null)
    if (!validate()) return
    setBusy(true)
    try {
      await api.joinWaitlist({
        name: f.name.trim(),
        phone: f.phone,
        email: f.email.trim() || undefined,
        notes: f.notes.trim() || undefined,
        lang,
      })
      setSent({ name: f.name.trim().split(/\s+/)[0], phone: normalizePhone(f.phone), email: f.email.trim() })
      await enterTheVoid()
    } catch (e) {
      if (e instanceof ApiError && e.code === 'invalid_phone') setErrors((x) => ({ ...x, phone: t.invalidPhone }))
      else if (e instanceof ApiError && e.fields?.email) setErrors((x) => ({ ...x, email: t.invalidEmail }))
      else setFormError(errorMessage(e, t))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="s-closed" data-phase={phase}>
      <header className="s-head">
        <div className="s-headin">
          <span />
          <span className="s-logo">
            <span dir="ltr">VOID</span>
          </span>
          <div className="s-navr">
            <LangSwitch />
          </div>
        </div>
      </header>

      <main className="s-main s-closed-main">
        <VoidStage className="s-closed-stage" engine={engine} coreRef={core}>
          {phase === 'sent' && <VoidCheck />}
        </VoidStage>

        {phase === 'sent' && sent ? (
          <div className="s-closed-copy">
            <p className="s-lab s-closed-in" style={{ animationDelay: '0.55s' }}>
              {t.onTheList}
            </p>
            <h1 className="s-closed-h s-closed-in" style={{ animationDelay: '0.7s' }} tabIndex={-1} ref={doneRef}>
              {t.thanksName(sent.name)}
            </h1>
            <p className="s-sub s-closed-in" style={{ animationDelay: '0.85s' }} role="status">
              {t.weWillCall(ltr(sent.phone))}
              {sent.email && ` ${t.weWillEmail(ltr(sent.email))}`}
            </p>
          </div>
        ) : (
          <>
            <div className="s-closed-copy">
              <p className="s-lab s-closed-in" style={{ animationDelay: '0.5s' }}>
                {t.closedKicker}
              </p>
              <h1 className="s-closed-h s-closed-in" style={{ animationDelay: '0.62s' }}>
                {t.closedHeading}
              </h1>
              <p className="s-sub s-closed-in" style={{ animationDelay: '0.74s' }}>
                {message}
              </p>
            </div>

            <div className="s-closed-formwrap s-closed-in" style={{ animationDelay: '0.86s' }}>
              <form className="s-form s-closed-form" ref={formRef} onSubmit={submit} noValidate>
                <div className="s-grid2">
                  <Field label={t.yourName} name="name" autoComplete="name" value={f.name} onChange={set('name')} error={errors.name} maxLength={120} />
                  <Field
                    label={t.phone}
                    name="phone"
                    type="tel"
                    autoComplete="tel"
                    inputMode="tel"
                    value={f.phone}
                    onChange={set('phone')}
                    error={errors.phone}
                    hint={t.phoneHint}
                    dir="ltr"
                  />
                </div>
                <Field
                  label={t.email}
                  name="email"
                  type="email"
                  autoComplete="email"
                  inputMode="email"
                  value={f.email}
                  onChange={set('email')}
                  error={errors.email}
                  aside={<span className="s-fhint">{t.optional}</span>}
                  dir="ltr"
                  maxLength={190}
                />
                <TextArea
                  label={t.notes}
                  name="notes"
                  value={f.notes}
                  onChange={set('notes')}
                  placeholder={t.notesPlaceholder}
                  aside={<span className="s-fhint">{t.optional}</span>}
                  rows={3}
                  maxLength={1000}
                />
                {formError && (
                  <p className="s-alert" role="alert">
                    {formError}
                  </p>
                )}
                <button type="submit" className="s-add s-full" disabled={busy || phase !== 'form'}>
                  {busy ? (
                    <>
                      <span className="s-spinner" aria-hidden="true" /> {t.sending}
                    </>
                  ) : (
                    t.notifyMe
                  )}
                </button>
              </form>
            </div>
          </>
        )}
      </main>

      <footer className="s-foot">
        <nav className="s-flinks" aria-label={t.footerNav}>
          <Link to={`/${lang}/track`}>{t.alreadyOrdered}</Link>
        </nav>
        <span className="s-lab s-copy" dir="ltr">
          © 2026 VOID
        </span>
        <div className="s-social">
          {social.map(({ label, href, Icon }) => (
            <a key={label} href={href!} aria-label={label} title={label} target="_blank" rel="noopener noreferrer">
              <Icon />
            </a>
          ))}
        </div>
      </footer>
    </div>
  )
}
