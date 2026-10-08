import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, useZones, type Address, type AddressInput } from '../api'
import { http } from '../api/http'
import { Field, Select } from '../components/Field'
import { OrderView, StatusPill, formatDate } from '../components/OrderView'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'
import { EMAIL_RE, errorMessage, isEgMobile } from '../lib/errors'
import { formatPrice } from '../lib/format'
import { isStaff, useAuth } from '../store/auth'
import { useToast } from '../store/toast'
import '../styles/forms.css'

// ------------------------------------------------------------------ signed out

function AuthForms() {
  const { lang, t } = useI18n()
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [mode, setMode] = useState<'in' | 'up' | 'forgot'>(params.get('mode') === 'signup' ? 'up' : 'in')
  const [f, setF] = useState({ name: '', email: '', password: '', phone: '' })
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }))

  const after = () => navigate(params.get('next') === 'checkout' ? `/${lang}/checkout` : `/${lang}/account`, { replace: true })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setInfo(null)
    if (!EMAIL_RE.test(f.email.trim())) return setError(t.invalidEmail)
    if (mode !== 'forgot' && f.password.length < (mode === 'up' ? 8 : 1)) return setError(t.passwordHint)
    if (mode === 'up' && !f.name.trim()) return setError(`${t.fullName}: ${t.required}`)
    if (mode === 'up' && f.phone && !isEgMobile(f.phone)) return setError(t.invalidPhone)
    setBusy(true)
    try {
      if (mode === 'in') {
        await login(f.email.trim(), f.password)
        after()
      } else if (mode === 'up') {
        await register(f.name.trim(), f.email.trim(), f.password, f.phone || undefined)
        after()
      } else {
        await http.post('/auth/forgot', { email: f.email.trim(), lang })
        setInfo(t.resetSent)
      }
    } catch (err) {
      setError(errorMessage(err, t))
    } finally {
      setBusy(false)
    }
  }

  const title = mode === 'in' ? t.signIn : mode === 'up' ? t.createAccount : t.forgotPassword
  return (
    <div className="s-narrow">
      {mode === 'forgot' ? (
        <h1 className="s-title">{title}</h1>
      ) : (
        <>
          <h1 className="s-title">{t.signInOrCreate}</h1>
          <p className="s-sub">{t.orContinueAsGuest}</p>
          <div className="s-tabs" role="tablist" style={{ marginBottom: 0 }}>
            {(['in', 'up'] as const).map((m) => (
              <button
                key={m}
                type="button"
                role="tab"
                aria-selected={mode === m}
                onClick={() => {
                  setMode(m)
                  setError(null)
                  setInfo(null)
                }}
              >
                {m === 'in' ? t.signIn : t.createAccount}
              </button>
            ))}
          </div>
        </>
      )}
      <form className="s-form" onSubmit={submit} noValidate style={{ marginTop: 24 }}>
        {mode === 'up' && <Field label={t.fullName} value={f.name} onChange={set('name')} autoComplete="name" />}
        <Field label={t.email} type="email" value={f.email} onChange={set('email')} autoComplete="email" dir="ltr" />
        {mode === 'up' && (
          <Field label={t.phone} type="tel" value={f.phone} onChange={set('phone')} autoComplete="tel" dir="ltr" aside={<span className="s-fhint">{t.optional}</span>} />
        )}
        {mode !== 'forgot' && (
          <Field
            label={t.password}
            type="password"
            value={f.password}
            onChange={set('password')}
            autoComplete={mode === 'up' ? 'new-password' : 'current-password'}
            hint={mode === 'up' ? t.passwordHint : undefined}
            aside={
              mode === 'in' ? (
                <button type="button" className="s-linkbtn" style={{ fontSize: 12 }} onClick={() => setMode('forgot')}>
                  {t.forgotPassword}
                </button>
              ) : undefined
            }
          />
        )}
        {error && (
          <p className="s-alert" role="alert">
            {error}
          </p>
        )}
        {info && (
          <p className="s-note" role="status">
            {info}
          </p>
        )}
        <button type="submit" className="s-add" disabled={busy}>
          {busy ? <span className="s-spinner" aria-hidden="true" /> : mode === 'forgot' ? t.sendResetLink : title}
        </button>
      </form>
      <p className="s-sub" style={{ marginTop: 24, textAlign: 'center' }}>
        {mode === 'in' ? (
          <>
            {t.noAccount}{' '}
            <button type="button" className="s-linkbtn" onClick={() => setMode('up')}>
              {t.createAccount}
            </button>
          </>
        ) : (
          <>
            {mode === 'up' && `${t.haveAccount} `}
            <button type="button" className="s-linkbtn" onClick={() => setMode('in')}>
              {t.signIn}
            </button>
          </>
        )}
      </p>
      <p style={{ textAlign: 'center' }}>
        <Link className="s-linkbtn" to={`/${lang}/track`}>
          {t.trackOrder}
        </Link>
      </p>
    </div>
  )
}

// ------------------------------------------------------------------ signed in

function OrdersTab() {
  const { lang, t } = useI18n()
  const { data, isLoading } = useQuery({ queryKey: ['my-orders'], queryFn: api.myOrders })
  if (isLoading) return <p className="s-sub">…</p>
  if (!data?.length) return <p className="s-sub">{t.noOrders}</p>
  return (
    <div>
      {data.map((o) => (
        <Link key={o.number} to={`/${lang}/account/orders/${o.number}`} className="s-line" style={{ gridTemplateColumns: '1fr auto' }}>
          <span className="s-linemeta">
            <span dir="ltr" style={{ fontFamily: 'var(--f-mono)', textAlign: 'start' }}>
              {o.number}
            </span>
            <span className="s-lab">
              {formatDate(o.createdAt, lang)} · {t.items(o.itemCount)}
            </span>
          </span>
          <span className="s-lineend">
            <span className="s-price">{formatPrice(o.total, lang)}</span>
            <StatusPill status={o.status} />
          </span>
        </Link>
      ))}
    </div>
  )
}

const EMPTY_ADDR: AddressInput = { name: '', phone: '', governorate: '', city: '', street: '', building: '', notes: '', isDefault: false }

function AddressForm({ initial, onDone }: { initial: Address | null; onDone: () => void }) {
  const { t, l } = useI18n()
  const qc = useQueryClient()
  const { data: zones = [] } = useZones()
  const [f, setF] = useState<AddressInput>(initial ?? EMPTY_ADDR)
  const [error, setError] = useState<string | null>(null)
  const set = (k: keyof AddressInput) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }))
  const save = useMutation({
    mutationFn: () => (initial ? api.updateAddress(initial.id, f) : api.addAddress(f)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['addresses'] })
      onDone()
    },
    onError: (e) => setError(errorMessage(e, t)),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!f.name.trim() || !f.city.trim() || !f.street.trim() || !f.governorate) return setError(t.required)
    if (!isEgMobile(f.phone)) return setError(t.invalidPhone)
    save.mutate()
  }
  return (
    <form className="s-form s-card-box" onSubmit={submit} noValidate>
      <div className="s-grid2">
        <Field label={t.fullName} value={f.name} onChange={set('name')} />
        <Field label={t.phone} type="tel" value={f.phone} onChange={set('phone')} dir="ltr" />
      </div>
      <Select label={t.governorate} value={f.governorate} onChange={set('governorate')}>
        <option value="">{t.chooseGovernorate}</option>
        {zones.map((z) => (
          <option key={z.code} value={z.code}>
            {l(z.name)}
          </option>
        ))}
      </Select>
      <div className="s-grid2">
        <Field label={t.city} value={f.city} onChange={set('city')} />
        <Field label={t.building} value={f.building} onChange={set('building')} />
      </div>
      <Field label={t.street} value={f.street} onChange={set('street')} />
      <Field label={t.addressNotes} value={f.notes} onChange={set('notes')} />
      <label className="s-check">
        <input type="checkbox" checked={f.isDefault} onChange={(e) => setF((x) => ({ ...x, isDefault: e.target.checked }))} />
        {t.makeDefault}
      </label>
      {error && <p className="s-alert">{error}</p>}
      <div style={{ display: 'flex', gap: 10 }}>
        <button type="submit" className="s-add" disabled={save.isPending}>
          {t.save}
        </button>
        <button type="button" className="s-cta" style={{ height: 52 }} onClick={onDone}>
          {t.cancel}
        </button>
      </div>
    </form>
  )
}

function AddressesTab() {
  const { t, l } = useI18n()
  const qc = useQueryClient()
  const { data: zones = [] } = useZones()
  const { data = [] } = useQuery({ queryKey: ['addresses'], queryFn: api.addresses })
  const [editing, setEditing] = useState<Address | 'new' | null>(null)
  const del = useMutation({ mutationFn: api.deleteAddress, onSuccess: () => qc.invalidateQueries({ queryKey: ['addresses'] }) })

  if (editing) return <AddressForm initial={editing === 'new' ? null : editing} onDone={() => setEditing(null)} />
  return (
    <div className="s-form">
      {data.map((a) => (
        <div key={a.id} className="s-card-box" style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
          <div style={{ fontSize: 14, lineHeight: 1.6 }}>
            <b style={{ fontWeight: 500 }}>{a.name}</b> {a.isDefault && <span className="s-pill">{t.default}</span>}
            <br />
            <span style={{ color: 'var(--muted)' }}>
              {a.street}
              {a.building ? `, ${a.building}` : ''}, {a.city}, {l(zones.find((z) => z.code === a.governorate)?.name) || a.governorate}
              <br />
              <bdi dir="ltr">{a.phone}</bdi>
            </span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'flex-end' }}>
            <button type="button" className="s-linkbtn" onClick={() => setEditing(a)}>
              {t.edit}
            </button>
            <button type="button" className="s-linkbtn" onClick={() => del.mutate(a.id)}>
              {t.delete}
            </button>
          </div>
        </div>
      ))}
      <button type="button" className="s-cta" onClick={() => setEditing('new')}>
        {t.addAddress}
      </button>
    </div>
  )
}

function ProfileTab() {
  const { t } = useI18n()
  const { user, setUser } = useAuth()
  const flash = useToast()
  const [name, setName] = useState(user?.name ?? '')
  const [phone, setPhone] = useState(user?.phone ?? '')
  const [pw, setPw] = useState({ current: '', next: '' })
  const [error, setError] = useState<string | null>(null)
  const [pwError, setPwError] = useState<string | null>(null)

  const saveProfile = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!name.trim()) return setError(t.required)
    if (phone && !isEgMobile(phone)) return setError(t.invalidPhone)
    try {
      setUser(await api.updateMe(name.trim(), phone))
      flash(t.saved)
    } catch (err) {
      setError(errorMessage(err, t))
    }
  }
  const savePw = async (e: FormEvent) => {
    e.preventDefault()
    setPwError(null)
    if (pw.next.length < 8) return setPwError(t.passwordHint)
    try {
      await api.changePassword(pw.current, pw.next)
      setPw({ current: '', next: '' })
      flash(t.saved)
    } catch (err) {
      setPwError(errorMessage(err, t))
    }
  }

  return (
    <div className="s-form" style={{ gap: 40, maxWidth: 460 }}>
      <form className="s-form" onSubmit={saveProfile} noValidate>
        <Field label={t.email} value={user?.email ?? ''} disabled dir="ltr" />
        <Field label={t.fullName} value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" />
        <Field label={t.phone} type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" dir="ltr" />
        {error && <p className="s-alert">{error}</p>}
        <button type="submit" className="s-add">
          {t.save}
        </button>
      </form>
      <form className="s-form" onSubmit={savePw} noValidate>
        <Field label={t.currentPassword} type="password" value={pw.current} onChange={(e) => setPw((x) => ({ ...x, current: e.target.value }))} autoComplete="current-password" />
        <Field label={t.newPassword} type="password" value={pw.next} onChange={(e) => setPw((x) => ({ ...x, next: e.target.value }))} autoComplete="new-password" hint={t.passwordHint} />
        {pwError && <p className="s-alert">{pwError}</p>}
        <button type="submit" className="s-cta">
          {t.save}
        </button>
      </form>
    </div>
  )
}

export function AccountPage() {
  const { lang, t } = useI18n()
  const { user, ready, logout } = useAuth()
  const [tab, setTab] = useState<'orders' | 'addresses' | 'profile'>('orders')
  useDocumentTitle(`${t.account} — VOID`)

  if (!ready) return <main className="s-main s-page" aria-busy="true" />
  return (
    <main className="s-main s-page">
      {!user ? (
        <AuthForms />
      ) : (
        <div className="s-wide" style={{ maxWidth: 760 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 16, flexWrap: 'wrap' }}>
            <h1 className="s-title">{user.name}</h1>
            <div style={{ display: 'flex', gap: 18 }}>
              {isStaff(user) && (
                <a className="s-linkbtn" href="/admin">
                  {t.adminPanel}
                </a>
              )}
              <button type="button" className="s-linkbtn" onClick={() => logout()}>
                {t.signOut}
              </button>
            </div>
          </div>
          <p className="s-sub" dir="ltr" style={{ textAlign: lang === 'ar' ? 'right' : 'left' }}>
            {user.email}
          </p>
          <div className="s-tabs" role="tablist">
            {(['orders', 'addresses', 'profile'] as const).map((k) => (
              <button key={k} type="button" role="tab" aria-selected={tab === k} onClick={() => setTab(k)}>
                {k === 'orders' ? t.myOrders : k === 'addresses' ? t.addresses : t.profile}
              </button>
            ))}
          </div>
          <div role="tabpanel">
            {tab === 'orders' && <OrdersTab />}
            {tab === 'addresses' && <AddressesTab />}
            {tab === 'profile' && <ProfileTab />}
          </div>
        </div>
      )}
    </main>
  )
}

export function AccountOrderPage() {
  const { number = '' } = useParams()
  const { lang, t } = useI18n()
  const { user, ready } = useAuth()
  const qc = useQueryClient()
  const { data, error } = useQuery({ queryKey: ['my-order', number], queryFn: () => api.myOrder(number), enabled: !!user })
  const update = (o: Awaited<ReturnType<typeof api.myOrder>>) => {
    qc.setQueryData(['my-order', number], o)
    qc.invalidateQueries({ queryKey: ['my-orders'] })
  }
  useDocumentTitle(`${number} — VOID`)
  if (ready && !user) return <Navigate to={`/${lang}/account`} replace />
  return (
    <main className="s-main s-page">
      <div className="s-narrow" style={{ maxWidth: 640 }}>
        <Link className="s-lab s-backlink" to={`/${lang}/account`} style={{ display: 'inline-flex', marginBottom: 20, color: 'var(--text)' }}>
          {lang === 'ar' ? '→' : '←'} {t.myOrders}
        </Link>
        {error && <p className="s-alert">{errorMessage(error, t)}</p>}
        {data && (
          <OrderView
            order={data}
            onCancel={async () => update(await api.cancelMyOrder(number))}
            onReturn={async (r) => update(await api.myReturn(number, r))}
          />
        )}
      </div>
    </main>
  )
}

export function ResetPasswordPage() {
  const { lang, t } = useI18n()
  const [params] = useSearchParams()
  const [pw, setPw] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  useDocumentTitle(`${t.resetPassword} — VOID`)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (pw.length < 8) return setError(t.passwordHint)
    try {
      await http.post('/auth/reset', { token: params.get('token') ?? '', password: pw })
      setDone(true)
    } catch (err) {
      setError(errorMessage(err, t))
    }
  }
  return (
    <main className="s-main s-page">
      <div className="s-narrow">
        <h1 className="s-title">{t.resetPassword}</h1>
        {done ? (
          <>
            <p className="s-note">{t.resetDone}</p>
            <Link className="s-add s-full" style={{ marginTop: 20 }} to={`/${lang}/account`}>
              {t.signIn}
            </Link>
          </>
        ) : (
          <form className="s-form" onSubmit={submit} noValidate style={{ marginTop: 24 }}>
            <Field label={t.newPassword} type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" hint={t.passwordHint} />
            {error && <p className="s-alert">{error}</p>}
            <button type="submit" className="s-add">
              {t.save}
            </button>
          </form>
        )}
      </div>
    </main>
  )
}
