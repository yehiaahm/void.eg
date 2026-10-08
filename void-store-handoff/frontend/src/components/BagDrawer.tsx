import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import type { Size } from '../api'
import { useQuote } from '../hooks/useQuote'
import { useI18n } from '../i18n'
import { formatPrice } from '../lib/format'
import { useBag } from '../store/bag'
import { CloseIcon } from './icons'
import { LineItem } from './LineItem'
import '../styles/forms.css'
import './BagDrawer.css'

export function BagDrawer() {
  const { lang, t } = useI18n()
  const bag = useBag()
  const { isOpen, setOpen } = bag
  const { data: quote, isFetching } = useQuote(bag.lines, { enabled: bag.isOpen })
  const panel = useRef<HTMLDivElement>(null)
  const closeBtn = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!isOpen) return
    const prev = document.activeElement as HTMLElement | null
    const de = document.documentElement
    de.setAttribute('data-lock', '')
    closeBtn.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
      if (e.key === 'Tab' && panel.current) {
        // keep focus inside the drawer
        const f = panel.current.querySelectorAll<HTMLElement>('a[href],button:not([disabled]),input,select,textarea')
        if (!f.length) return
        const first = f[0]
        const last = f[f.length - 1]
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault()
          last.focus()
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault()
          first.focus()
        }
      }
    }
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('keydown', onKey)
      de.removeAttribute('data-lock')
      prev?.focus?.()
    }
  }, [isOpen, setOpen])

  if (!bag.isOpen) return null
  const close = () => bag.setOpen(false)
  const byKey = new Map(quote?.items.map((q) => [`${q.slug}|${q.size}`, q]))
  const blocked = quote?.items.some((q) => q.issue) ?? false

  return (
    <div className="s-drawer-wrap">
      <div className="s-scrim" onClick={close} aria-hidden="true" />
      <div className="s-drawer" role="dialog" aria-modal="true" aria-label={t.bag} ref={panel}>
        <div className="s-drawer-head">
          <h2 className="s-h">
            {t.bag} <span className="s-lab">({bag.count})</span>
          </h2>
          <button type="button" className="s-ib" onClick={close} aria-label={t.closeBag} ref={closeBtn}>
            <CloseIcon size={20} />
          </button>
        </div>

        {bag.lines.length === 0 ? (
          <div className="s-drawer-empty">
            <p>{t.bagEmpty}</p>
            <Link className="s-cta" to={`/${lang}`} state={{ scrollTo: 'shop' }} onClick={close}>
              {t.continueShopping}
            </Link>
          </div>
        ) : (
          <>
            <div className="s-drawer-body" aria-busy={isFetching}>
              {bag.lines.map((line) => {
                const q = byKey.get(`${line.slug}|${line.size}`)
                return (
                  <LineItem
                    key={`${line.slug}|${line.size}`}
                    slug={line.slug}
                    size={line.size}
                    qty={line.qty}
                    name={q?.name}
                    image={q?.image}
                    lineTotal={q?.lineTotal}
                    unitPrice={q ? q.unitPrice : undefined}
                    issue={q?.issue}
                    available={q?.available}
                    onQty={(n) => bag.setQty(line.slug, line.size as Size, n)}
                    onRemove={() => bag.remove(line.slug, line.size as Size)}
                    onNavigate={close}
                  />
                )
              })}
            </div>
            <div className="s-drawer-foot">
              <dl className="s-totals">
                <dt>{t.subtotal}</dt>
                <dd>{quote ? formatPrice(quote.subtotal, lang) : '—'}</dd>
                <dt>{t.shippingFee}</dt>
                <dd style={{ fontFamily: 'inherit', fontSize: 13, color: 'var(--muted)' }}>{t.calculatedAtCheckout}</dd>
              </dl>
              {blocked && <p className="s-ferr">{t.fixBag}</p>}
              {blocked || !quote ? (
                <button type="button" className="s-add s-full" disabled>
                  {t.checkout}
                </button>
              ) : (
                <Link className="s-add s-full" to={`/${lang}/checkout`} onClick={close}>
                  {t.checkout}
                </Link>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
