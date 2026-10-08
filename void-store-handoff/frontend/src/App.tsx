import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Suspense, lazy, useEffect } from 'react'
import { Link, Navigate, Outlet, RouterProvider, ScrollRestoration, createBrowserRouter, useLocation, useParams } from 'react-router-dom'
import { useSettings } from './api'
import { AnnouncementBar } from './components/AnnouncementBar'
import { Footer } from './components/Footer'
import { Header } from './components/Header'
import { I18nProvider, isLang, preferredLang, useI18n } from './i18n'
import { HomePage } from './pages/HomePage'
import { NotFoundPage } from './pages/NotFoundPage'
import { AuthProvider, isStaff, useAuth } from './store/auth'
import { BagProvider, useBag } from './store/bag'
import { ToastProvider } from './store/toast'
import { WishlistProvider } from './store/wishlist'

// Everything beyond home + product loads on demand to keep the first page light.
const ProductPage = lazy(() => import('./pages/ProductPage').then((m) => ({ default: m.ProductPage })))
const BagDrawer = lazy(() => import('./components/BagDrawer').then((m) => ({ default: m.BagDrawer })))
const CheckoutPage = lazy(() => import('./pages/CheckoutPage').then((m) => ({ default: m.CheckoutPage })))
const OrderPlacedPage = lazy(() => import('./pages/OrderPlacedPage').then((m) => ({ default: m.OrderPlacedPage })))
const TrackPage = lazy(() => import('./pages/TrackPage').then((m) => ({ default: m.TrackPage })))
const WishlistPage = lazy(() => import('./pages/WishlistPage').then((m) => ({ default: m.WishlistPage })))
const InfoPage = lazy(() => import('./pages/InfoPage').then((m) => ({ default: m.InfoPage })))
const ContactPage = lazy(() => import('./pages/ContactPage').then((m) => ({ default: m.ContactPage })))
const AccountPage = lazy(() => import('./pages/AccountPage').then((m) => ({ default: m.AccountPage })))
const AccountOrderPage = lazy(() => import('./pages/AccountPage').then((m) => ({ default: m.AccountOrderPage })))
const ResetPasswordPage = lazy(() => import('./pages/AccountPage').then((m) => ({ default: m.ResetPasswordPage })))
const AdminApp = lazy(() => import('./admin/AdminApp'))
const ClosedPage = lazy(() => import('./pages/ClosedPage').then((m) => ({ default: m.ClosedPage })))

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

const pageFallback = <main className="s-main" aria-busy="true" />

/** The drawer's code loads the first time the bag is opened. */
function LazyBag() {
  const { isOpen } = useBag()
  return isOpen ? (
    <Suspense fallback={null}>
      <BagDrawer />
    </Suspense>
  ) : null
}

/** Still reachable while the store is closed: order tracking, accounts, policy pages. */
const OPEN_WHEN_CLOSED = /^\/(en|ar)\/(track|account|order|pages)(\/|$)/
const CLOSED_HINT = 'void.closed'

/**
 * Whether the owner has closed the store. Until /settings answers, trust the server's
 * <html data-closed> (production) or the last answer we saw, so the shop doesn't flash first.
 */
function useStoreClosed(): boolean {
  const { data } = useSettings()
  const closed = data?.storeClosed
  useEffect(() => {
    if (closed === undefined) return
    try {
      if (closed) localStorage.setItem(CLOSED_HINT, '1')
      else localStorage.removeItem(CLOSED_HINT)
    } catch {
      /* storage unavailable */
    }
  }, [closed])
  if (closed !== undefined) return closed
  if (document.documentElement.dataset.closed === '1') return true
  try {
    return localStorage.getItem(CLOSED_HINT) === '1'
  } catch {
    return false
  }
}

/** Staff can still browse (and test) the closed store — remind them that shoppers can't. */
function StaffClosedBar() {
  const { t } = useI18n()
  return (
    <div className="s-staffbar" role="status">
      <span>{t.staffClosedBar}</span>
      <Link to="/admin">{t.openAdmin} ↗</Link>
    </div>
  )
}

function StoreLayout() {
  const { lang } = useParams()
  const { pathname } = useLocation()
  const { user } = useAuth()
  const closed = useStoreClosed()
  if (!isLang(lang)) return <Navigate to={`/${preferredLang()}`} replace />
  const staff = isStaff(user)
  if (closed && !staff && !OPEN_WHEN_CLOSED.test(pathname)) {
    return (
      <I18nProvider lang={lang}>
        <Suspense fallback={pageFallback}>
          <ClosedPage />
        </Suspense>
      </I18nProvider>
    )
  }
  return (
    <I18nProvider lang={lang}>
      {closed && staff && <StaffClosedBar />}
      <AnnouncementBar />
      <Header />
      <Suspense fallback={pageFallback}>
        <Outlet />
      </Suspense>
      <Footer />
      <LazyBag />
      <ScrollRestoration getKey={(loc) => loc.pathname.replace(/^\/(en|ar)/, '')} />
    </I18nProvider>
  )
}

const router = createBrowserRouter([
  { path: '/', element: <Navigate to={`/${preferredLang()}`} replace /> },
  {
    path: '/admin/*',
    element: (
      <Suspense fallback={null}>
        <AdminApp />
      </Suspense>
    ),
  },
  {
    path: '/:lang',
    element: <StoreLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'product/:slug', element: <ProductPage /> },
      { path: 'checkout', element: <CheckoutPage /> },
      { path: 'order/:number', element: <OrderPlacedPage /> },
      { path: 'track', element: <TrackPage /> },
      { path: 'wishlist', element: <WishlistPage /> },
      { path: 'pages/contact', element: <ContactPage /> },
      { path: 'pages/:slug', element: <InfoPage /> },
      { path: 'account', element: <AccountPage /> },
      { path: 'account/orders/:number', element: <AccountOrderPage /> },
      { path: 'account/reset', element: <ResetPasswordPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <WishlistProvider>
          <BagProvider>
            <ToastProvider>
              <RouterProvider router={router} />
            </ToastProvider>
          </BagProvider>
        </WishlistProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
