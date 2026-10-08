import { Link } from 'react-router-dom'
import type { Product } from '../api/types'
import { useI18n } from '../i18n'
import { ProductMedia, hasPhoto } from './ProductMedia'
import { WishButton } from './WishButton'
import './ProductCard.css'

export function ProductCard({ product, index }: { product: Product; index: number }) {
  const { lang, t, l } = useI18n()
  const soldOut = product.variants.every((v) => v.stock <= 0)
  const photo = hasPhoto(product, 'front')
  return (
    <div className="s-card" style={{ animationDelay: `${index * 0.05}s` }}>
      <Link className="s-cardlink" to={`/${lang}/product/${product.slug}`} state={{ fromShop: true }}>
        <span className={`s-img${photo ? ' s-img-photo' : ''}`}>
          <ProductMedia product={product} kind="front" className="s-front" sizes="(max-width: 900px) 50vw, 25vw" />
          <ProductMedia product={product} kind="back" className="s-back" sizes="(max-width: 900px) 50vw, 25vw" />
          {!photo && <span className="s-ph">{t.productPhoto}</span>}
          {soldOut && <span className="s-tag">{t.soldOut}</span>}
        </span>
        <span className="s-cardtxt">
          <span className="s-name">
            <bdi>{l(product.name)}</bdi>
          </span>
        </span>
      </Link>
      <WishButton slug={product.slug} name={l(product.name)} />
    </div>
  )
}
