import { Suspense, lazy, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useGoShop } from '../hooks/useGoShop'
import { useI18n } from '../i18n'
import { BAG_BUMP } from '../lib/flyToBag'
import { useAuth } from '../store/auth'
import { useBag } from '../store/bag'
import { useWishlist } from '../store/wishlist'
import { BagIcon, CloseIcon, HeartIcon, MenuIcon, UserIcon } from './icons'
import { LangSwitch } from './LangSwitch'
import './Header.css'

const MobileMenu = lazy(() => import('./MobileMenu').then((m) => ({ default: m.MobileMenu })))

export function Header() {
  const { lang, t } = useI18n()
  const { count, setOpen: openBag } = useBag()
  const { count: saved } = useWishlist()
  const { user } = useAuth()
  const [bump, setBump] = useState(0)

  // Bag icon pops when something lands in it (see lib/flyToBag).
  useEffect(() => {
    const on = () => setBump((n) => n + 1)
    window.addEventListener(BAG_BUMP, on)
    return () => window.removeEventListener(BAG_BUMP, on)
  }, [])
  const { pathname } = useLocation()
  const goShop = useGoShop()
  const [menu, setMenu] = useState(false)
  const headRef = useRef<HTMLElement>(null)

  // Close the menu on navigation.
  useEffect(() => setMenu(false), [pathname])

  useEffect(() => {
    const de = document.documentElement
    if (menu) {
      de.style.setProperty('--menu-top', `${Math.max(0, headRef.current?.getBoundingClientRect().bottom ?? 0)}px`)
      de.setAttribute('data-lock', '')
    } else de.removeAttribute('data-lock')
    if (!menu) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setMenu(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [menu])

  const home = `/${lang}`

  const onShop = (e: React.MouseEvent) => {
    setMenu(false)
    goShop(e)
  }

  return (
    <>
      <header className="s-head" ref={headRef}>
        <div className="s-headin">
          <nav className="s-navl" aria-label={t.primaryNav}>
            <a className="s-link s-lab" href={`${home}#shop`} onClick={onShop} style={{ color: 'var(--text)' }}>
              {t.shop}
            </a>
            <Link className="s-link s-lab" to={`${home}/track`} style={{ color: 'var(--text)' }}>
              {t.trackOrder}
            </Link>
          </nav>
          <button
            type="button"
            className="s-ib s-menubtn"
            aria-label={menu ? t.closeMenu : t.openMenu}
            aria-expanded={menu}
            aria-controls="s-menu"
            onClick={() => setMenu((m) => !m)}
          >
            {menu ? <CloseIcon /> : <MenuIcon />}
          </button>
          <Link
            className="s-logo"
            to={home}
            aria-label="VOID"
            onClick={() => {
              setMenu(false)
              if (pathname === home) window.scrollTo({ top: 0, behavior: 'smooth' })
            }}
          >
            <span dir="ltr">VOID</span>
          </Link>
          <div className="s-navr">
            <LangSwitch />
            <Link className="s-ib s-wishbtn" to={`${home}/wishlist`} aria-label={t.wishlistLabel(saved)}>
              <HeartIcon filled={saved > 0} />
              {saved > 0 && (
                <span className="s-dot" dir="ltr">
                  {saved}
                </span>
              )}
            </Link>
            <Link className="s-ib" to={`${home}/account`} aria-label={t.accountLabel(user?.name ?? null)} title={t.accountLabel(user?.name ?? null)}>
              <UserIcon />
              {user && <span className="s-online" aria-hidden="true" />}
            </Link>
            <button
              type="button"
              className="s-ib"
              data-bag-target
              aria-label={t.bagLabel(count)}
              aria-haspopup="dialog"
              onClick={() => {
                setMenu(false)
                openBag(true)
              }}
            >
              <span key={bump} className={bump ? 's-bagpop' : undefined} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <BagIcon />
                <span className="s-badge" dir="ltr">
                  {count}
                </span>
              </span>
            </button>
          </div>
        </div>
      </header>
      {menu && (
        <Suspense fallback={<div className="s-menu" />}>
          <MobileMenu onShop={onShop} onClose={() => setMenu(false)} />
        </Suspense>
      )}
    </>
  )
}

