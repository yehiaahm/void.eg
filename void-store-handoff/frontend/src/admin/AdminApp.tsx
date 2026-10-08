import { useQuery } from '@tanstack/react-query'
import { Suspense, lazy, useEffect, useState, type FormEvent } from 'react'
import { Link, NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { ApiError } from '../api/http'
import { isStaff, useAuth } from '../store/auth'
import './admin.css'
import { adminApi } from './api'
import { AdminI18n, useT } from './i18n'
import { useStoreStatus } from './StoreSwitch'
import { Field, Icons } from './ui'

const Dashboard = lazy(() => import('./pages/Dashboard'))
const Orders = lazy(() => import('./pages/Orders'))
const OrderDetail = lazy(() => import('./pages/OrderDetail'))
const Products = lazy(() => import('./pages/Products'))
const ProductEditor = lazy(() => import('./pages/ProductEditor'))
const Returns = lazy(() => import('./pages/Returns'))
const Customers = lazy(() => import('./pages/Customers'))
const Drops = lazy(() => import('./pages/Drops'))
const Coupons = lazy(() => import('./pages/Coupons'))
const Shipping = lazy(() => import('./pages/Shipping'))
const Content = lazy(() => import('./pages/Content'))
const Staff = lazy(() => import('./pages/Staff'))
const Waitlist = lazy(() => import('./pages/Waitlist'))
const Messages = lazy(() => import('./pages/Messages'))

function Login() {
  const { t } = useT()
  const { login, logout, user } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(user && !isStaff(user) ? t.notStaff : null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const u = await login(email.trim(), password)
      if (!isStaff(u)) {
        await logout()
        setError(t.notStaff)
      }
    } catch (err) {
      setError(err instanceof ApiError && err.code === 'bad_credentials' ? t.badCredentials : err instanceof ApiError && err.code === 'rate_limited' ? err.message : t.genericError)
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="a-login">
      <form className="a-card a-form" onSubmit={submit}>
        <div className="a-brand" style={{ padding: '0 0 8px' }}>
          <b dir="ltr">VOID</b>
          <span className="a-faint">{t.admin}</span>
        </div>
        <Field label={t.email} type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} dir="ltr" required />
        <Field label={t.password} type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error && <p className="a-error">{error}</p>}
        <button type="submit" className="a-btn a-btn-primary" disabled={busy} style={{ height: 40 }}>
          {t.signIn}
        </button>
      </form>
    </div>
  )
}

function Shell() {
  const { t, lang, setLang } = useT()
  const { user, logout } = useAuth()
  const { pathname } = useLocation()
  const [navOpen, setNavOpen] = useState(false)
  const role = user!.role
  const isAdmin = role === 'ADMIN' || role === 'OWNER'

  useEffect(() => setNavOpen(false), [pathname])

  const { data: newCount } = useQuery({
    queryKey: ['admin', 'new-count'],
    queryFn: () => adminApi.orders({ status: 'NEW' }).then((r) => r.total),
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  })

  const { data: returnCount } = useQuery({
    queryKey: ['admin', 'returns-count'],
    queryFn: () => adminApi.returns({ status: 'REQUESTED' }).then((r) => r.total),
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  })

  const { data: unreadMessages } = useQuery({
    queryKey: ['admin', 'messages-unread'],
    queryFn: adminApi.unreadMessages,
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  })

  const { data: store } = useStoreStatus()

  const links = [
    { to: '/admin', label: t.dashboard, icon: Icons.dashboard, end: true },
    { to: '/admin/orders', label: t.orders, icon: Icons.orders, count: newCount },
    { to: '/admin/returns', label: t.returns, icon: Icons.returns, count: returnCount },
    { to: '/admin/products', label: t.products, icon: Icons.products },
    { to: '/admin/customers', label: t.customers, icon: Icons.customers },
    { to: '/admin/messages', label: t.messages, icon: Icons.messages, count: unreadMessages },
    { to: '/admin/waitlist', label: t.waitlist, icon: Icons.waitlist, count: store?.waiting },
    ...(isAdmin
      ? [
          { to: '/admin/drops', label: t.drops, icon: Icons.drops },
          { to: '/admin/coupons', label: t.coupons, icon: Icons.coupons },
          { to: '/admin/shipping', label: t.shipping, icon: Icons.shipping },
          { to: '/admin/content', label: t.content, icon: Icons.content },
        ]
      : []),
    ...(role === 'OWNER' ? [{ to: '/admin/staff', label: t.staff, icon: Icons.staff }] : []),
  ]

  return (
    <div className="a-app" data-nav={navOpen ? 'open' : undefined}>
      <aside className="a-side" aria-label={t.menu}>
        <div className="a-brand">
          <b dir="ltr">VOID</b>
          <span className="a-faint">{t.admin}</span>
        </div>
        {store && (
          <Link to="/admin" className="a-storechip" data-closed={store.closed}>
            {store.closed ? t.storeClosed : t.storeOpen}
          </Link>
        )}
        <nav className="a-nav">
          {links.map((l) => (
            <NavLink key={l.to} to={l.to} end={l.end}>
              <l.icon />
              {l.label}
              {!!l.count && <span className="a-count">{l.count}</span>}
            </NavLink>
          ))}
        </nav>
        <div className="a-side-foot">
          <div className="a-me">
            {user!.name}
            <small dir="ltr">{user!.email}</small>
          </div>
          <button type="button" onClick={() => setLang(lang === 'en' ? 'ar' : 'en')}>
            {t.language}
          </button>
          <a href={`/${lang}`} target="_blank" rel="noopener">
            {t.viewStore} ↗
          </a>
          <button type="button" onClick={() => logout()}>
            {t.signOut}
          </button>
        </div>
      </aside>
      {navOpen && <div className="a-scrim" onClick={() => setNavOpen(false)} />}
      <div style={{ minWidth: 0 }}>
        <div className="a-top">
          <button type="button" className="a-btn a-btn-icon" style={{ border: 0 }} onClick={() => setNavOpen(true)} aria-label={t.menu}>
            <Icons.menu />
          </button>
          <b dir="ltr">VOID</b>
          <span style={{ width: 32 }} />
        </div>
        <main className="a-main">
          <Suspense fallback={<p className="a-muted">{t.loading}</p>}>
            <Routes>
              <Route index element={<Dashboard />} />
              <Route path="orders" element={<Orders />} />
              <Route path="orders/:number" element={<OrderDetail />} />
              <Route path="returns" element={<Returns />} />
              <Route path="returns/:number" element={<Returns />} />
              <Route path="products" element={<Products />} />
              <Route path="products/new" element={<ProductEditor />} />
              <Route path="products/:id" element={<ProductEditor />} />
              <Route path="customers" element={<Customers />} />
              <Route path="customers/:id" element={<Customers />} />
              <Route path="waitlist" element={<Waitlist />} />
              <Route path="messages" element={<Messages />} />
              {isAdmin && <Route path="drops" element={<Drops />} />}
              {isAdmin && <Route path="coupons" element={<Coupons />} />}
              {isAdmin && <Route path="shipping" element={<Shipping />} />}
              {isAdmin && <Route path="content" element={<Content />} />}
              {role === 'OWNER' && <Route path="staff" element={<Staff />} />}
              <Route path="*" element={<Navigate to="/admin" replace />} />
            </Routes>
          </Suspense>
        </main>
      </div>
    </div>
  )
}

function Gate() {
  const { user, ready } = useAuth()
  if (!ready) return null
  return user && isStaff(user) ? <Shell /> : <Login />
}

export default function AdminApp() {
  return (
    <AdminI18n>
      <Gate />
    </AdminI18n>
  )
}
