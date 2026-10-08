import { Link } from 'react-router-dom'
import { useProducts } from '../api'
import { ProductCard } from '../components/ProductCard'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'
import { useAuth } from '../store/auth'
import { useWishlist } from '../store/wishlist'
import '../styles/forms.css'

export function WishlistPage() {
  const { lang, t } = useI18n()
  const { user } = useAuth()
  const wish = useWishlist()
  const { data: products, isLoading } = useProducts()
  useDocumentTitle(`${t.wishlist} — VOID`)

  // keep the order the customer saved them in (newest first)
  const saved = (products ?? [])
    .filter((p) => wish.has(p.slug))
    .sort((a, b) => wish.slugs.indexOf(a.slug) - wish.slugs.indexOf(b.slug))

  return (
    <main className="s-main s-sec" aria-busy={isLoading}>
      <div className="s-sechead">
        <h1 className="s-h">{t.wishlist}</h1>
        {saved.length > 0 && <span className="s-lab">{t.items(saved.length)}</span>}
      </div>
      {!isLoading && saved.length === 0 ? (
        <div className="s-narrow" style={{ textAlign: 'center', display: 'grid', gap: 20, padding: '40px 0' }}>
          <p className="s-sub" style={{ margin: 0 }}>
            {t.wishlistEmpty}
          </p>
          <Link className="s-cta" to={`/${lang}`} state={{ scrollTo: 'shop' }} style={{ justifySelf: 'center' }}>
            {t.continueShopping}
          </Link>
        </div>
      ) : (
        <div className="s-grid">
          {saved.map((p, i) => (
            <ProductCard key={p.slug} product={p} index={i} />
          ))}
        </div>
      )}
      {!user && saved.length > 0 && (
        <p className="s-sub" style={{ marginTop: 32 }}>
          {t.wishlistGuestNote}{' '}
          <Link className="s-linkbtn" to={`/${lang}/account`}>
            {t.signIn}
          </Link>
        </p>
      )}
    </main>
  )
}
