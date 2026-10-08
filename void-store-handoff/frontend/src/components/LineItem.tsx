import { Link } from 'react-router-dom'
import { assetUrl, type Localized, type QuoteLine } from '../api'
import { useI18n } from '../i18n'
import { formatPrice } from '../lib/format'
import { MinusIcon, PlusIcon } from './icons'
import { PRODUCT_ART } from './productArt'

interface Props {
  slug: string
  size: string
  qty: number
  name?: Localized | string | null
  image?: string | null
  lineTotal?: number | null
  unitPrice?: number | null
  issue?: QuoteLine['issue']
  available?: number
  onQty?: (qty: number) => void
  onRemove?: () => void
  onNavigate?: () => void
}

export function Thumb({ slug, image }: { slug: string; image?: string | null }) {
  const art = PRODUCT_ART[slug]
  return (
    <span className="s-thumb">
      {image ? (
        <img src={assetUrl(image)} alt="" loading="lazy" />
      ) : art ? (
        <svg viewBox="0 0 240 280" aria-hidden="true" dangerouslySetInnerHTML={{ __html: art.front }} />
      ) : null}
    </span>
  )
}

export function LineItem({ slug, size, qty, name, image, lineTotal, unitPrice, issue, available = 10, onQty, onRemove, onNavigate }: Props) {
  const { lang, t, l } = useI18n()
  const label = typeof name === 'string' ? name : l(name) || slug
  const max = Math.max(1, Math.min(10, available))
  return (
    <div className="s-line">
      <Link to={`/${lang}/product/${slug}`} onClick={onNavigate} tabIndex={-1} aria-hidden="true">
        <Thumb slug={slug} image={image} />
      </Link>
      <div className="s-linemeta">
        <Link to={`/${lang}/product/${slug}`} onClick={onNavigate}>
          <bdi>{label}</bdi>
        </Link>
        <span className="s-lab">{onQty ? t.sizeLabel(size) : `${t.sizeLabel(size)} · ${t.qtyLabel(qty)}`}</span>
        {issue && (
          <span className="s-ferr" role="status">
            {issue === 'insufficient_stock' ? t.issue.insufficient_stock(available) : t.issue[issue]}
          </span>
        )}
        {onQty && issue !== 'unavailable' && issue !== 'out_of_stock' && (
          <span className="s-mini-qty">
            <button type="button" aria-label={t.decrease} onClick={() => onQty(qty - 1)} disabled={qty <= 1}>
              <MinusIcon />
            </button>
            <output aria-live="polite">{qty}</output>
            <button type="button" aria-label={t.increase} onClick={() => onQty(qty + 1)} disabled={qty >= max}>
              <PlusIcon />
            </button>
          </span>
        )}
      </div>
      <div className="s-lineend">
        <span className="s-price">{lineTotal != null && !issue ? formatPrice(lineTotal, lang) : unitPrice !== undefined ? formatPrice(unitPrice, lang) : ''}</span>
        {onRemove && (
          <button type="button" className="s-linkbtn" style={{ fontSize: 12 }} onClick={onRemove}>
            {t.remove}
          </button>
        )}
      </div>
    </div>
  )
}
