import { Link, useLocation } from 'react-router-dom'
import { useI18n } from '../i18n'

/** `EN / ع` — same page in the other language. Styles in Header.css. */
export function LangSwitch() {
  const { lang, t, switchHref } = useI18n()
  const { pathname, search, hash } = useLocation()
  const other = switchHref(pathname) + search + hash
  return (
    <div role="group" aria-label={t.language} className="s-lang">
      {lang === 'en' ? (
        <>
          <span aria-current="true" className="s-lab" style={{ color: 'var(--text)' }}>
            EN
          </span>
          <span className="s-lab" aria-hidden="true">/</span>
          <Link to={other} lang="ar" hrefLang="ar" className="s-lang-ar" preventScrollReset>
            ع
          </Link>
        </>
      ) : (
        <>
          <Link to={other} lang="en" hrefLang="en" className="s-lab s-lang-en" preventScrollReset>
            EN
          </Link>
          <span className="s-lab" aria-hidden="true">/</span>
          <span aria-current="true" className="s-lang-ar" style={{ color: 'var(--text)' }}>
            ع
          </span>
        </>
      )}
    </div>
  )
}
