import { Link } from 'react-router-dom'
import type { Product } from '../api/types'
import { useI18n } from '../i18n'
import { ProductMedia, hasPhoto } from './ProductMedia'
import { WishButton } from './WishButton'
import './ProductCard.css'

export function ProductCard({ product, index }: { product: Product; index: number }) {
  const { lang, t, l } = useI18n()
  const soldOut = product.variants.every((v) => v.stock <= 0)
  // A transparent cut-out of the piece (admin slot "Shop card") is shown on a black tile with a white
  // glow; the model photo then appears on hover. Without one the card behaves as before.
  const cut = hasPhoto(product, 'cutout')
  const photo = cut || hasPhoto(product, 'front')
  return (
    <div className="s-card" style={{ animationDelay: `${index * 0.05}s` }}>
      {/* The name is not shown on the card (owner request, 2026-11-14) — only on the product page. It stays
          in aria-label so the link still has an accessible name. */}
      <Link className="s-cardlink" to={`/${lang}/product/${product.slug}`} state={{ fromShop: true }} aria-label={l(product.name)}>
        <span className={`s-img${photo ? ' s-img-photo' : ''}${cut ? ' s-img-cut' : ''}`}>
          <ProductMedia product={product} kind={cut ? 'cutout' : 'front'} className="s-front" sizes="(max-width: 900px) 50vw, 25vw" />
          <ProductMedia product={product} kind={cut ? 'front' : 'back'} className="s-back" sizes="(max-width: 900px) 50vw, 25vw" />
          {!photo && <span className="s-ph">{t.productPhoto}</span>}
          {soldOut && <span className="s-tag">{t.soldOut}</span>}
        </span>
      </Link>
      <WishButton slug={product.slug} name={l(product.name)} />
    </div>
  )
}
