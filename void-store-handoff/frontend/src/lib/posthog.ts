// Product analytics (PostHog) for the storefront and the admin.
// The project key is a public, write-only key (it is meant to ship in client bundles); VITE_POSTHOG_KEY overrides it.
import posthog from 'posthog-js'

const KEY = (import.meta.env.VITE_POSTHOG_KEY as string | undefined) || 'phc_sUMShRkJ5hy6UWvN4PwFsXNvX7yKK9vrTMdBc9m2yoCZ'
const HOST = (import.meta.env.VITE_POSTHOG_HOST as string | undefined) || 'https://us.i.posthog.com'

if (import.meta.env.PROD && typeof window !== 'undefined' && KEY) {
  posthog.init(KEY, {
    api_host: HOST,
    // SPA-aware pageviews/pageleaves on client-side route changes.
    defaults: '2025-05-24',
    person_profiles: 'identified_only',
    // Never record what customers type (names, phones, addresses).
    session_recording: { maskAllInputs: true },
  })
}

export { posthog }
