import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { assetUrl, useProduct, useProducts, useSettings } from '../api'
import type { ImageKind, Product, Size } from '../api/types'
import { BackIcon, MinusIcon, PlusIcon } from '../components/icons'
import { ProductCard } from '../components/ProductCard'
import { ProductMedia, hasPhoto } from '../components/ProductMedia'
import { useDocumentTitle, useJsonLd } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'
import { dropNo, formatPrice } from '../lib/format'
import { MAX_QTY, useBag } from '../store/bag'
import { useToast } from '../store/toast'
import { WishButton } from '../components/WishButton'
import { flyToBag } from '../lib/flyToBag'
import { NotFoundPage } from './NotFoundPage'
import './ProductPage.css'

const KINDS: ImageKind[] = ['front', 'back', 'detail', 'body']

export function ProductPage() {
  const { slug = '' } = useParams()
  const { data: product, isLoading } = useProduct(slug)

  // Phone layout: lift the toast above the sticky bar.
  useEffect(() => {
    document.documentElement.setAttribute('data-pdp', '')
    return () => document.documentElement.removeAttribute('data-pdp')
  }, [])

  if (isLoading) return <main className="s-main s-pdpin" aria-busy="true" />
  if (!product) return <NotFoundPage />
  // key resets size / qty / gallery when moving between products
  return <ProductView key={product.slug} product={product} />
}

function ProductView({ product }: { product: Product }) {
  const { lang, t, l } = useI18n()
  const { data: settings } = useSettings()
  const { data: all = [] } = useProducts()
  const bag = useBag()
  const flash = useToast()
  const navigate = useNavigate()
  const location = useLocation()

  const [size, setSize] = useState<Size | null>(null)
  const [qty, setQty] = useState(1)
  const [hint, setHint] = useState(false)
  const [gi, setGi] = useState(0)
  const gal = useRef<HTMLDivElement>(null)
  const sizeFitRef = useRef<HTMLDetailsElement>(null)

  const name = l(product.name)
  const desc = l(product.description)
  const photo = product.images.find((i) => i.kind === 'front') ?? product.images[0]
  const photoUrl = photo ? new URL(assetUrl(photo.url), window.location.origin).href : undefined
  useDocumentTitle(`${name} — VOID`, desc, photoUrl)
  useJsonLd({
    '@context': 'https://schema.org',
    '@type': 'Product',
    name,
    description: desc,
    sku: product.variants.find((v) => v.sku)?.sku ?? undefined,
    brand: { '@type': 'Brand', name: 'VOID' },
    image: product.images.map((i) => new URL(assetUrl(i.url), window.location.origin).href),
    ...(product.price != null && {
      offers: {
        '@type': 'Offer',
        priceCurrency: 'EGP',
        price: (product.price / 100).toFixed(2),
        availability: product.variants.some((v) => v.stock > 0) ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock',
        url: window.location.href,
      },
    }),
  })

  const drop = [settings?.currentDrop, settings?.nextDrop].find((d) => d?.id === product.dropId) ?? settings?.currentDrop
  const dn = drop ? dropNo(drop.number) : '01'
  const variant = product.variants.find((v) => v.size === size)
  const soldOut = product.variants.every((v) => v.stock <= 0)
  const maxQty = Math.max(1, Math.min(MAX_QTY, variant?.stock ?? MAX_QTY))
  const price = formatPrice(product.price, lang)
  const more = all.filter((p) => p.slug !== product.slug && p.dropId === product.dropId)

  const pick = (s: Size) => {
    setSize(s)
    setHint(false)
    const st = product.variants.find((v) => v.size === s)?.stock ?? MAX_QTY
    setQty((q) => Math.max(1, Math.min(q, MAX_QTY, st)))
  }

  const add = () => {
    if (!size) {
      setHint(true)
      flash(t.pickToast)
      return
    }
    bag.add(product.slug, size, qty)
    flyToBag(gal.current?.children[gi] ?? null)
    // FSI/PDI keep the Latin name + size readable inside Arabic text
    flash(`${t.added} — ⁨${name} · ${size}${qty > 1 ? ` × ${qty}` : ''}⁩`)
  }

  const back = (e: React.MouseEvent) => {
    e.preventDefault()
    if ((location.state as { fromShop?: boolean } | null)?.fromShop) navigate(-1)
    else navigate(`/${lang}`, { state: { scrollTo: 'shop' } })
  }

  const onGalScroll = () => {
    const el = gal.current
    if (!el) return
    const i = Math.min(KINDS.length - 1, Math.max(0, Math.round(Math.abs(el.scrollLeft) / (el.clientWidth || 1))))
    if (i !== gi) setGi(i)
  }
  const goImg = (i: number) => {
    const g = gal.current
    if (g) {
      const rtl = getComputedStyle(g).direction === 'rtl'
      g.scrollTo({ left: (rtl ? -1 : 1) * i * (g.clientWidth || 1), behavior: 'smooth' })
    }
    setGi(i)
  }

  const openSizeGuide = () => {
    const d = sizeFitRef.current
    if (!d) return
    d.open = true
    d.scrollIntoView({ behavior: 'smooth', block: 'center' })
    d.querySelector('summary')?.focus({ preventScroll: true })
  }

  const addBtn = (
    <button type="button" className="s-add" onClick={add} disabled={soldOut}>
      {soldOut ? t.soldOut : t.addToBag}
    </button>
  )

  const specs: [string, string][] = [
    [t.spec.fit, l(product.details.fit)],
    [t.spec.fabric, l(product.details.fabric)],
    [t.spec.weight, l(product.details.weight)],
    [t.spec.colour, l(product.details.colour)],
    [t.spec.care, l(product.details.care)],
    [t.spec.sku, variant?.sku ?? product.variants.find((v) => v.sku)?.sku ?? t.skuPh],
  ]

  return (
    <main className="s-main s-pdpin">
      <a className="s-backlink s-lab" href={`/${lang}#shop`} onClick={back}>
        <BackIcon />
        <span>{t.backTo(dn)}</span>
      </a>
      <div className="s-pgrid">
        <div>
          <div className="s-gal" ref={gal} onScroll={onGalScroll}>
            {KINDS.map((k, i) => (
              <figure key={k} aria-label={t.imageOf(i + 1, KINDS.length)}>
                <ProductMedia product={product} kind={k} sizes="(max-width: 900px) 100vw, 30vw" />
                {!hasPhoto(product, k) && (
                  <figcaption className="s-ph">
                    {t.productPhoto} · {t.imageKind[k]}
                  </figcaption>
                )}
              </figure>
            ))}
          </div>
          <div className="s-dots">
            {KINDS.map((k, i) => (
              <button key={k} type="button" className="s-dot" aria-label={t.imageOf(i + 1, KINDS.length)} aria-current={gi === i} onClick={() => goImg(i)}>
                <span />
              </button>
            ))}
          </div>
        </div>

        <div className="s-info">
          <div className="s-stack">
            {drop && <p className="s-lab">{t.dropLabel(dn, l(drop.name))}</p>}
            <div className="s-namerow">
              <h1 className="s-pname">
                <bdi>{name}</bdi>
              </h1>
              <WishButton slug={product.slug} name={name} className="s-wish s-wish-inline" size={20} />
            </div>
            <p className="s-price s-pprice">
              {product.compareAtPrice != null && product.price != null && product.compareAtPrice > product.price && (
                <s className="s-was">{formatPrice(product.compareAtPrice, lang)}</s>
              )}
              {price}
            </p>
          </div>

          <div className="s-stack">
            <div className="s-row">
              <span className="s-lab" id="s-size-label">
                {t.size}
              </span>
              <button type="button" className="s-lab s-guide" onClick={openSizeGuide}>
                {t.sizeGuide}
              </button>
            </div>
            <div role="group" aria-labelledby="s-size-label" className="s-sizes">
              {product.variants.map((v) => {
                const out = v.stock <= 0
                return (
                  <button
                    key={v.size}
                    type="button"
                    className="s-size"
                    aria-pressed={size === v.size}
                    aria-label={out ? t.sizeSoldOut(v.size) : undefined}
                    disabled={out}
                    onClick={() => pick(v.size)}
                  >
                    {v.size}
                  </button>
                )
              })}
            </div>
            {hint && (
              <p role="alert" className="s-hint">
                {t.pickHint}
              </p>
            )}
          </div>

          <div className="s-row s-center">
            <span className="s-lab">{t.quantity}</span>
            <div className="s-qty">
              <button type="button" aria-label={t.decrease} onClick={() => setQty((q) => Math.max(1, q - 1))} disabled={qty <= 1}>
                <MinusIcon />
              </button>
              <output aria-live="polite">{qty}</output>
              <button type="button" aria-label={t.increase} onClick={() => setQty((q) => Math.min(maxQty, q + 1))} disabled={qty >= maxQty}>
                <PlusIcon />
              </button>
            </div>
          </div>

          <div className="s-addwrap">{addBtn}</div>
          <p className="s-desc">{l(product.description)}</p>

          <div>
            <details className="s-det">
              <summary>{t.details}</summary>
              <div>
                <dl className="s-specs">
                  {specs.map(([k, v]) => (
                    <div key={k} style={{ display: 'contents' }}>
                      <dt>{k}</dt>
                      <dd>{v}</dd>
                    </div>
                  ))}
                </dl>
              </div>
            </details>
            <details className="s-det" ref={sizeFitRef}>
              <summary>{t.sizeFit}</summary>
              <div className="s-pre">{l(product.sizeFit)}</div>
            </details>
            <details className="s-det">
              <summary>{t.shippingReturns}</summary>
              <div className="s-pre">{l(settings?.shippingReturns)}</div>
            </details>
          </div>
        </div>
      </div>

      {more.length > 0 && (
        <section className="s-more" aria-label={t.moreFrom(dn)}>
          <h2 className="s-h" style={{ marginBottom: 20 }}>
            {t.moreFrom(dn)}
          </h2>
          <div className="s-grid">
            {more.map((p, i) => (
              <ProductCard key={p.slug} product={p} index={i} />
            ))}
          </div>
        </section>
      )}

      <div className="s-bar">
        <div className="s-barinfo">
          <span className="s-barname">
            <bdi>{name}</bdi>
          </span>
          <span className="s-price">{price}</span>
        </div>
        {addBtn}
      </div>
    </main>
  )
}
