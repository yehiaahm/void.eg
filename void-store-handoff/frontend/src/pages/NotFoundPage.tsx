import { Link } from 'react-router-dom'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'

export function NotFoundPage() {
  const { lang, t } = useI18n()
  useDocumentTitle(`${t.notFound} — VOID`)
  return (
    <main className="s-main s-sec" style={{ display: 'grid', placeContent: 'center', gap: 18, textAlign: 'center' }}>
      <p className="s-lab" dir="ltr">
        404
      </p>
      <h1 className="s-h" style={{ fontSize: 22 }}>
        {t.notFound}
      </h1>
      <p style={{ margin: 0, color: 'var(--muted)' }}>{t.notFoundBody}</p>
      <Link className="s-cta" to={`/${lang}`} style={{ justifySelf: 'center', marginTop: 8 }}>
        {t.backHome}
      </Link>
    </main>
  )
}
