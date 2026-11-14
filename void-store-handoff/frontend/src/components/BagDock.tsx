import { useEffect, useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { assetUrl, useProducts } from '../api'
import { useI18n } from '../i18n'
import { BAG_BUMP } from '../lib/flyToBag'
import { useBag } from '../store/bag'
import { BagIcon } from './icons'
import './BagDock.css'

/** The bag is out of the way on these pages (checkout has its own summary). */
const HIDDEN_ON = /^\/(en|ar)\/(checkout|order)(\/|$)/
const MAX_THUMBS = 3

/**
 * Floating bag at the bottom of the screen. It is always mounted (so the "add to bag" animation has a
 * landing spot) but only shown while the bag has something in it: the product picture flies into it,
 * it pops, and it stays with small pictures of what was added until the shopper opens the bag.
 */
export function BagDock() {
  const { lang, t, l } = useI18n()
  const { lines, count, isOpen, setOpen } = useBag()
  const { pathname } = useLocation()
  const { data: products } = useProducts()
  const [bump, setBump] = useState(0)

  const eligible = !HIDDEN_ON.test(pathname)
  const shown = eligible && count > 0 && !isOpen

  useEffect(() => {
    const on = () => setBump((n) => n + 1)
    window.addEventListener(BAG_BUMP, on)
    return () => window.removeEventListener(BAG_BUMP, on)
  }, [])

  // Let toasts and the page bottom make room for the dock.
  useEffect(() => {
    const de = document.documentElement
    if (shown) de.setAttribute('data-dock', '')
    else de.removeAttribute('data-dock')
    return () => de.removeAttribute('data-dock')
  }, [shown])

  // Newest piece first, one picture per product.
  const thumbs = useMemo(() => {
    const seen = new Set<string>()
    const out: { slug: string; url: string | null; name: string }[] = []
    for (const line of [...lines].reverse()) {
      if (seen.has(line.slug) || out.length >= MAX_THUMBS) continue
      seen.add(line.slug)
      const p = products?.find((x) => x.slug === line.slug)
      const img = p?.images.find((i) => i.kind === 'front') ?? p?.images.find((i) => i.kind === 'cutout')
      out.push({ slug: line.slug, url: img ? img.url : null, name: p ? l(p.name) : line.slug })
    }
    return out
  }, [lines, products, l])

  if (!eligible) return null

  return (
    <div className="s-dockw" data-shown={shown ? '' : undefined} dir={lang === 'ar' ? 'rtl' : 'ltr'}>
      <button
        type="button"
        className="s-dock"
        aria-label={t.bagLabel(count)}
        aria-haspopup="dialog"
        tabIndex={shown ? 0 : -1}
        aria-hidden={shown ? undefined : true}
        onClick={() => setOpen(true)}
      >
        <span key={bump} className={`s-dock-ico${bump ? ' s-bagpop' : ''}`} data-dock-target>
          <BagIcon />
          <span className="s-dock-count" dir="ltr">
            {count}
          </span>
        </span>
        {thumbs.length > 0 && (
          <span className="s-dock-thumbs" aria-hidden="true">
            {thumbs.map((th) => (
              <span className="s-dock-thumb" key={th.slug}>
                {th.url && <img src={assetUrl(th.url)} alt="" />}
              </span>
            ))}
          </span>
        )}
        <span className="s-dock-label">{t.viewBag}</span>
      </button>
    </div>
  )
}
