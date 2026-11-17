import { useId, useMemo, useState, type KeyboardEvent } from 'react'
import type { ShippingZone } from '../api/types'
import { useI18n } from '../i18n'
import { formatPrice } from '../lib/format'
import { resolveZone, searchZones } from '../lib/zones'
import './GovernorateField.css'

interface Props {
  zones: ShippingZone[]
  /** selected zone code ('' = none yet) */
  code: string
  /** what is typed; null = show the selected zone's name */
  text: string | null
  onChange: (next: { code: string; text: string | null }) => void
  error?: string | null
  disabled?: boolean
  name?: string
}

/**
 * Governorate as a type-ahead field instead of a dropdown: the zone is picked the moment what the shopper types can only
 * mean one governorate (English or Arabic, any common spelling), so the shipping fee shows up right away. Pressing a
 * suggestion, Enter, or leaving the field with a clear match also works; the full list never shows fees.
 */
export function GovernorateField({ zones, code, text, onChange, error, disabled, name = 'governorate' }: Props) {
  const { lang, t, l } = useI18n()
  const id = useId()
  const listId = `${id}-list`
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const [touched, setTouched] = useState(false)

  const selected = useMemo(() => zones.find((z) => z.code === code) ?? null, [zones, code])
  const shown = text ?? (selected ? l(selected.name) : '')
  const typed = text !== null && text.trim() !== ''
  const matches = useMemo(() => (typed ? searchZones(zones, text ?? '') : []), [zones, text, typed])
  const noMatch = typed && matches.length === 0
  // nothing left to choose once the one remaining suggestion is already the selected zone
  const listOpen = open && typed && matches.length > 0 && !(selected && matches.length === 1)

  const type = (value: string) => {
    setOpen(true)
    setActive(0)
    onChange({ code: resolveZone(zones, value)?.code ?? '', text: value })
  }
  const pick = (z: ShippingZone) => {
    setOpen(false)
    onChange({ code: z.code, text: null })
  }
  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      if (!listOpen) return
      e.preventDefault()
      setActive((i) => (i + (e.key === 'ArrowDown' ? 1 : -1) + matches.length) % matches.length)
    } else if (e.key === 'Enter' && (listOpen || (typed && selected))) {
      e.preventDefault() // confirm the suggestion, do not submit the form
      pick(listOpen ? matches[Math.min(active, matches.length - 1)] : selected!)
    } else if (e.key === 'Escape' && listOpen) {
      e.preventDefault()
      setOpen(false)
    }
  }
  const onBlur = () => {
    setOpen(false)
    setTouched(true)
    // leave a clean full name behind when the typed text already means one governorate
    if (typed && selected) onChange({ code: selected.code, text: null })
  }

  const fee = selected ? (selected.fee === 0 ? t.free : formatPrice(selected.fee, lang)) : null
  const problem = error || (noMatch && touched ? t.governorateNone : null)
  const activeId = listOpen ? `${id}-o${Math.min(active, matches.length - 1)}` : undefined

  return (
    <div className="s-field s-gov">
      <label htmlFor={id}>
        <span className="s-lab">{t.governorate}</span>
      </label>
      <div className="s-gov-box">
        <input
          id={id}
          name={name}
          className="s-input"
          role="combobox"
          aria-expanded={listOpen}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={activeId}
          aria-invalid={!!problem}
          aria-describedby={problem ? `${id}-err` : fee ? `${id}-fee` : undefined}
          autoComplete="off"
          autoCapitalize="words"
          spellCheck={false}
          enterKeyHint="next"
          placeholder={t.typeGovernorate}
          value={shown}
          disabled={disabled}
          onChange={(e) => type(e.target.value)}
          onFocus={() => setOpen(true)}
          onBlur={onBlur}
          onKeyDown={onKey}
        />
        {listOpen && (
          <ul className="s-gov-list" id={listId} role="listbox" aria-label={t.governorate}>
            {matches.map((z, i) => (
              <li
                key={z.code}
                id={`${id}-o${i}`}
                role="option"
                aria-selected={i === active}
                className={i === active ? 's-gov-opt is-active' : 's-gov-opt'}
                // mouseDown (not click) so the input keeps focus and onBlur does not close the list first
                onMouseDown={(e) => {
                  e.preventDefault()
                  pick(z)
                }}
                onMouseEnter={() => setActive(i)}
              >
                {l(z.name)}
              </li>
            ))}
          </ul>
        )}
      </div>
      {problem ? (
        <p className="s-ferr" id={`${id}-err`} role="alert">
          {problem}
        </p>
      ) : fee ? (
        <p className="s-fhint s-gov-fee" id={`${id}-fee`} aria-live="polite">
          {t.governorateShipping(fee)}
        </p>
      ) : null}
    </div>
  )
}
