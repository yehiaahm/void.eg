import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useProducts } from '../api'
import { useI18n } from '../i18n'
import { useAuth } from '../store/auth'
import { useWishlist } from '../store/wishlist'
import { SearchIcon } from './icons'

export function MobileMenu({ onShop, onClose }: { onShop: (e: React.MouseEvent) => void; onClose: () => void }) {
  const { lang, t, l } = useI18n()
  const { data: products = [] } = useProducts()
  const { user } = useAuth()
  const { count: saved } = useWishlist()
  const [q, setQ] = useState('')

  const results = useMemo(() => {
    const needle = q.trim().toLowerCase()
    if (!needle) return null
    return products.filter((p) =>
      [p.name.en, p.name.ar, p.category.en, p.category.ar].some((s) => s.toLowerCase().includes(needle)),
    )
  }, [q, products])

  return (
    <div className="s-menu" id="s-menu" role="dialog" aria-modal="true" aria-label={t.menu}>
      <form role="search" className="s-msearch" onSubmit={(e) => e.preventDefault()}>
        <label htmlFor="s-q" className="s-sr">
          {t.search}
        </label>
        <SearchIcon />
        <input id="s-q" type="search" placeholder={t.searchPlaceholder} value={q} onChange={(e) => setQ(e.target.value)} autoComplete="off" />
      </form>
      {results && (
        <ul className="s-results">
          {results.length === 0 && <li className="s-empty">{t.noResults}</li>}
          {results.map((p) => (
            <li key={p.slug}>
              <Link to={`/${lang}/product/${p.slug}`} onClick={onClose}>
                <bdi>{l(p.name)}</bdi>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <a className="s-menu-link" href={`/${lang}#shop`} onClick={onShop}>
        {t.shop}
      </a>
      <Link className="s-menu-link" to={`/${lang}/account`} onClick={onClose}>
        {user ? t.account : t.signIn}
      </Link>
      <Link className="s-menu-link" to={`/${lang}/wishlist`} onClick={onClose}>
        {t.wishlist}
        {saved > 0 && <span className="s-lab" style={{ marginInlineStart: 10 }}>({saved})</span>}
      </Link>
      <Link className="s-menu-link" to={`/${lang}/track`} onClick={onClose}>
        {t.trackOrder}
      </Link>
    </div>
  )
}
