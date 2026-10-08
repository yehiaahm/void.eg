import { Link } from 'react-router-dom'
import { useSettings } from '../api'
import { useI18n } from '../i18n'
import { InstagramIcon, TikTokIcon } from './icons'
import './Footer.css'

export function Footer() {
  const { lang, t } = useI18n()
  const { data } = useSettings()
  const links = [
    { label: t.shipping, to: `/${lang}/pages/shipping` },
    { label: t.returns, to: `/${lang}/pages/returns` },
    { label: t.privacy, to: `/${lang}/pages/privacy` },
    { label: t.contact, to: `/${lang}/pages/contact` },
    { label: t.trackOrder, to: `/${lang}/track` },
  ]
  // [PLACEHOLDER] until the owner adds the URLs in Admin → Content.
  const social = [
    { label: 'Instagram', href: data?.instagramUrl, Icon: InstagramIcon },
    { label: 'TikTok', href: data?.tiktokUrl, Icon: TikTokIcon },
  ]

  return (
    <footer className="s-foot">
      <nav className="s-flinks" aria-label={t.footerNav}>
        {links.map((x) => (
          <Link key={x.to} to={x.to}>
            {x.label}
          </Link>
        ))}
      </nav>
      <span className="s-lab s-copy" dir="ltr">
        © 2026 VOID
      </span>
      <div className="s-social">
        {social.map(({ label, href, Icon }) =>
          href ? (
            <a key={label} href={href} aria-label={label} title={label} target="_blank" rel="noopener noreferrer">
              <Icon />
            </a>
          ) : (
            <span key={label} className="s-social-off" aria-hidden="true" title={label}>
              <Icon />
            </span>
          ),
        )}
      </div>
    </footer>
  )
}
