import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import './toast.css'

const Ctx = createContext<(text: string) => void>(() => {})

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ text: string; n: number } | null>(null)
  const timer = useRef<number | undefined>(undefined)

  const flash = useCallback((text: string) => {
    window.clearTimeout(timer.current)
    setToast((t) => ({ text, n: (t?.n ?? 0) + 1 }))
    timer.current = window.setTimeout(() => setToast(null), 2600)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return (
    <Ctx.Provider value={flash}>
      {children}
      <div className="s-toastw" aria-live="polite">
        {toast && (
          // key restarts the entry animation for consecutive toasts
          <div className="s-toast" key={toast.n}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M5 12.5l4.5 4.5L19 7.5" />
            </svg>
            <span>{toast.text}</span>
          </div>
        )}
      </div>
    </Ctx.Provider>
  )
}

export const useToast = () => useContext(Ctx)
