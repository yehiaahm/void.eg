import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { OrderStatus } from '../api'
import { useI18n } from '../i18n'
import { orderHref, useMyOrders, type MyOrder } from '../store/myOrders'
import { CloseIcon } from './icons'
import './MyOrders.css'

const FLOW: OrderStatus[] = ['NEW', 'CONFIRMED', 'SHIPPED', 'DELIVERED']

/** Four segments: received → confirmed → shipped → delivered. Cancelled / returned orders show no progress. */
export function OrderProgress({ status }: { status: OrderStatus }) {
  const { t } = useI18n()
  const at = FLOW.indexOf(status)
  if (at < 0) return null
  return (
    <span className="s-prog" role="img" aria-label={t.status[status]}>
      {FLOW.map((s, i) => (
        <i key={s} data-done={i <= at} />
      ))}
    </span>
  )
}

/** Small dot for the "My orders" link while something is on its way. */
export function ActiveOrdersDot() {
  const { active } = useMyOrders()
  return active.length ? <span className="s-navdot" aria-hidden="true" /> : null
}

const HIDDEN_KEY = 'void.orderstrip.hidden'
const stripKey = (o: MyOrder) => `${o.number}:${o.status}`

function loadHidden(): string[] {
  try {
    const raw = JSON.parse(sessionStorage.getItem(HIDDEN_KEY) ?? '[]')
    return Array.isArray(raw) ? raw.filter((x): x is string => typeof x === 'string') : []
  } catch {
    return []
  }
}

/**
 * Home page strip: the customer's order in progress and where it is, the moment they open the store.
 * Closing it hides that order at that status for this visit; when the status changes it comes back.
 */
export function OrderStrip() {
  const { lang, t } = useI18n()
  const { active } = useMyOrders()
  const [hidden, setHidden] = useState<string[]>(loadHidden)
  const shown = active.filter((o) => !hidden.includes(stripKey(o)))
  const first = shown[0]
  if (!first) return null

  const hide = () => {
    const next = [...hidden, ...shown.map(stripKey)]
    setHidden(next)
    try {
      sessionStorage.setItem(HIDDEN_KEY, JSON.stringify(next))
    } catch {
      /* storage unavailable: it just comes back next page load */
    }
  }
  const more = shown.length - 1
  return (
    <div className="s-ostrip" role="status">
      <Link className="s-ostrip-main" to={more > 0 ? `/${lang}/track` : orderHref(lang, first)}>
        <span className="s-lab s-ostrip-lab">{t.orderStripLabel}</span>
        <span className="s-mono s-ostrip-no" dir="ltr">
          {first.number}
        </span>
        <OrderProgress status={first.status} />
        <span className="s-ostrip-st">{t.status[first.status]}</span>
        {more > 0 && <span className="s-lab">{t.orderStripMore(more)}</span>}
        <span className="s-ostrip-go">
          {t.orderStripTrack} {lang === 'ar' ? '←' : '→'}
        </span>
      </Link>
      <button type="button" className="s-ostrip-x" aria-label={t.orderStripClose} onClick={hide}>
        <CloseIcon />
      </button>
    </div>
  )
}
