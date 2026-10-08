import { assetUrl, type ImageKind, type Product } from '../api'
import { useI18n } from '../i18n'
import { PRODUCT_ART } from './productArt'

/** Real photo when uploaded, otherwise the design's line-drawing stand-in (front/back only). */
export function ProductMedia({ product, kind, className, sizes }: { product: Product; kind: ImageKind; className?: string; sizes?: string }) {
  const { l } = useI18n()
  const img = product.images.find((i) => i.kind === kind)
  if (img) {
    return <img className={className} src={assetUrl(img.url)} alt={l(img.alt) || ''} loading="lazy" decoding="async" sizes={sizes} />
  }
  const art = PRODUCT_ART[product.slug]
  if (art && (kind === 'front' || kind === 'back')) {
    return <svg className={className} viewBox="0 0 240 280" aria-hidden="true" dangerouslySetInnerHTML={{ __html: art[kind] }} />
  }
  return null
}

export const hasPhoto = (p: Product, kind: ImageKind) => p.images.some((i) => i.kind === kind)
