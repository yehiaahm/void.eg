// Error monitoring (Sentry) for the storefront and the admin dashboard.
// The DSN is a public, write-only key (it is meant to ship in client bundles); VITE_SENTRY_DSN overrides it.
import * as Sentry from '@sentry/react'

const env = import.meta.env as Record<string, string | boolean | undefined>
const DSN =
  (env.VITE_SENTRY_DSN as string | undefined) ??
  'https://03c7ec805c8f5fda0ccaa6c66ba273f1@o4512447745818624.ingest.de.sentry.io/4512447896617040'

if (import.meta.env.PROD && DSN) {
  Sentry.init({
    dsn: DSN,
    environment: (env.VITE_SENTRY_ENVIRONMENT as string | undefined) || 'production',
    integrations: [Sentry.browserTracingIntegration()],
    tracesSampleRate: 0.1,
    initialScope: { tags: { app: 'void-web' } },
    // Noise from extensions, in-app browsers and flaky mobile networks.
    ignoreErrors: ['ResizeObserver loop', 'Non-Error promise rejection captured', /^Load failed$/, /Failed to fetch dynamically imported module/],
    denyUrls: [/^chrome-extension:\/\//, /^moz-extension:\/\//, /^safari-web-extension:\/\//],
  })
}

export { Sentry }
