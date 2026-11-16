import { Link, useLocation, useParams } from 'react-router-dom'
import { VoidCheck, VoidStage } from '../components/VoidStage'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'
import { formatPrice } from '../lib/format'
import { useAuth } from '../store/auth'
import { useMyOrders } from '../store/myOrders'
import { useToast } from '../store/toast'
import '../styles/forms.css'
import './OrderPlacedPage.css'

/**
 * Order confirmation: a small black hole opens (same intro as the hero), particles spiral in,
 * a check mark is drawn inside the event horizon, then the message and order number arrive.
 */
export function OrderPlacedPage() {
  const { number = '' } = useParams()
  const { lang, t } = useI18n()
  const { user } = useAuth()
  const flash = useToast()
  const state = (useLocation().state ?? {}) as { email?: string; total?: number; trackingToken?: string }
  useDocumentTitle(`${t.thanks} — VOID`)
  // After a refresh the router state is gone; the device still remembers the order and its private link.
  const known = useMyOrders().orders.find((o) => o.number === number)
  const token = state.trackingToken ?? known?.token
  const total = state.total ?? known?.total

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(number)
      flash(t.copied)
    } catch {
      /* clipboard blocked — the number stays selectable */
    }
  }

  // The private link that opens this order on any device; the customer can keep it or send it to themselves.
  const link = token ? `${window.location.origin}/${lang}/track?order=${encodeURIComponent(number)}&t=${encodeURIComponent(token)}` : null
  const copyLink = async () => {
    if (!link) return
    try {
      await navigator.clipboard.writeText(link)
      flash(t.linkCopied)
    } catch {
      /* clipboard blocked — the WhatsApp button still works */
    }
  }

  return (
    <main className="s-main s-done">
      <VoidStage>
        <VoidCheck />
      </VoidStage>

      <div className="s-done-copy">
        <p className="s-lab s-done-in" style={{ animationDelay: '1.5s' }}>
          {t.thanks}
        </p>
        <h1 className="s-title s-done-in" style={{ margin: 0, animationDelay: '1.65s' }} role="status">
          {t.orderPlaced}
        </h1>
        <p className="s-sub s-done-in" style={{ margin: 0, animationDelay: '1.8s' }}>
          {t.orderTagline}
        </p>

        <div className="s-card-box s-done-in s-done-number" style={{ animationDelay: '2s' }}>
          <p className="s-lab" style={{ margin: 0 }}>
            {t.orderNumber}
          </p>
          <div className="s-done-numrow">
            <span dir="ltr">{number}</span>
            <button type="button" className="s-linkbtn" onClick={copy}>
              {t.copy}
            </button>
          </div>
          {total != null && (
            <p className="s-price" style={{ margin: '10px 0 0' }}>
              {formatPrice(total, lang)} · {t.cod}
            </p>
          )}
        </div>

        {state.email && (
          <p className="s-sub s-done-in" style={{ margin: 0, animationDelay: '2.15s' }}>
            {t.confirmationSent(state.email)}
          </p>
        )}
        {link && (
          <div className="s-done-in" style={{ animationDelay: '2.2s', display: 'grid', gap: 8, justifyItems: 'center' }}>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, justifyContent: 'center' }}>
              <button type="button" className="s-linkbtn" onClick={copyLink}>
                {t.copyLink}
              </button>
              <a className="s-linkbtn" href={`https://wa.me/?text=${encodeURIComponent(t.shareMessage(number, link))}`} target="_blank" rel="noopener noreferrer">
                {t.sendLinkWhatsapp}
              </a>
            </div>
            <p className="s-fhint" style={{ margin: 0, maxWidth: 420, textAlign: 'center' }}>
              {t.saveLinkHint}
            </p>
          </div>
        )}
        {!user && (
          <p className="s-sub s-done-in" style={{ margin: 0, animationDelay: '2.25s' }}>
            {t.accountNudge}{' '}
            <Link className="s-linkbtn" to={`/${lang}/account?mode=signup`}>
              {t.accountNudgeLink}
            </Link>
          </p>
        )}
        <div className="s-done-in s-done-actions" style={{ animationDelay: '2.3s' }}>
          <Link className="s-add" to={user ? `/${lang}/account/orders/${number}` : `/${lang}/track?order=${number}${token ? `&t=${encodeURIComponent(token)}` : ''}`}>
            {t.trackOrder}
          </Link>
          <Link className="s-cta" to={`/${lang}`}>
            {t.continueShopping}
          </Link>
        </div>
      </div>
    </main>
  )
}
