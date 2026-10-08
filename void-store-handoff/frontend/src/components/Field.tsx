import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'

interface Base {
  label: ReactNode
  error?: string | null
  hint?: ReactNode
  aside?: ReactNode
}

function Shell({ id, label, error, hint, aside, children }: Base & { id: string; children: ReactNode }) {
  return (
    <div className="s-field">
      <label htmlFor={id}>
        <span className="s-lab">{label}</span>
        {aside}
      </label>
      {children}
      {error ? (
        <p className="s-ferr" id={`${id}-err`} role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="s-fhint" id={`${id}-hint`}>
          {hint}
        </p>
      ) : null}
    </div>
  )
}

const describe = (id: string, error?: string | null, hint?: ReactNode) => (error ? `${id}-err` : hint ? `${id}-hint` : undefined)

export function Field({ label, error, hint, aside, ...rest }: Base & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId()
  return (
    <Shell id={id} label={label} error={error} hint={hint} aside={aside}>
      <input id={id} className="s-input" aria-invalid={!!error} aria-describedby={describe(id, error, hint)} {...rest} />
    </Shell>
  )
}

export function TextArea({ label, error, hint, aside, ...rest }: Base & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId()
  return (
    <Shell id={id} label={label} error={error} hint={hint} aside={aside}>
      <textarea id={id} className="s-input" aria-invalid={!!error} aria-describedby={describe(id, error, hint)} {...rest} />
    </Shell>
  )
}

export function Select({ label, error, hint, aside, children, ...rest }: Base & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId()
  return (
    <Shell id={id} label={label} error={error} hint={hint} aside={aside}>
      <select id={id} className="s-input" aria-invalid={!!error} aria-describedby={describe(id, error, hint)} {...rest}>
        {children}
      </select>
    </Shell>
  )
}
