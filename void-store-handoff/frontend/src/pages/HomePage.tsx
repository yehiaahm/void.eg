import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useProducts } from '../api'
import { Hero } from '../components/hero/Hero'
import { ProductCard } from '../components/ProductCard'
import { scrollToShop } from '../hooks/useGoShop'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'

export function HomePage() {
  const { t } = useI18n()
  const { data: products } = useProducts()
  const location = useLocation()
  const navigate = useNavigate()
  useDocumentTitle(t.title)

  // Arrived via "Shop" from another page, or with #shop in the URL.
  const wantsShop = (location.state as { scrollTo?: string } | null)?.scrollTo === 'shop' || location.hash === '#shop'
  useEffect(() => {
    if (!wantsShop || !products) return
    requestAnimationFrame(() => scrollToShop(false))
    if (location.state) navigate(location.pathname + location.hash, { replace: true, state: null, preventScrollReset: true })
  }, [wantsShop, products, location, navigate])

  return (
    <main id="top" className="s-main">
      <Hero />
      <section id="shop" className="s-sec">
        <div className="s-sechead">
          <h1 className="s-h">{t.shopAll}</h1>
          {products && <span className="s-lab">{t.productsCount(products.length)}</span>}
        </div>
        <div className="s-grid">
          {products
            ? products.map((p, i) => <ProductCard key={p.slug} product={p} index={i} />)
            : [0, 1, 2, 3].map((i) => (
                <span key={i} className="s-card s-card-ph" aria-hidden="true">
                  <span className="s-img" />
                  <span className="s-cardtxt">
                    <span className="s-name">&nbsp;</span>
                  </span>
                </span>
              ))}
        </div>
      </section>
    </main>
  )
}
