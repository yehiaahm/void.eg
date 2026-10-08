import { Link, useLocation, useParams } from 'react-router-dom'
import { VoidCheck, VoidStage } from '../components/VoidStage'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'
import { formatPrice } from '../lib/format'
import { useAuth } from '../store/auth'
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
  const state = (useLocation().state ?? {}) as { email?: string; total?: number }
  useDocumentTitle(`${t.thanks} — VOID`)

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(number)
      flash(t.copied)
    } catch {
      /* clipboard blocked — the number stays selectable */
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
          {state.total != null && (
            <p className="s-price" style={{ margin: '10px 0 0' }}>
              {formatPrice(state.total, lang)} · {t.cod}
            </p>
          )}
        </div>

        {state.email && (
          <p className="s-sub s-done-in" style={{ margin: 0, animationDelay: '2.15s' }}>
            {t.confirmationSent(state.email)}
          </p>
        )}
        <div className="s-done-in s-done-actions" style={{ animationDelay: '2.3s' }}>
          <Link className="s-add" to={user ? `/${lang}/account/orders/${number}` : `/${lang}/track?order=${number}`}>
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
