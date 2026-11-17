import type { ShippingZone } from '../api/types'

// Matching what a shopper types (English or Arabic, any spelling) — or what the map calls the governorate
// ("Cairo Governorate", "محافظة القاهرة") — to a delivery zone.

/** Other common spellings per zone code (the zone's own English / Arabic names are always matched too). */
const ALIASES: Record<string, string[]> = {
  cairo: ['al qahira', 'el qahira', 'qahira', 'kahira'],
  giza: ['gizeh', 'al jizah', 'el giza', 'jiza'],
  alexandria: ['alex', 'iskandariya', 'iskendereyya', 'el iskandaria'],
  qalyubia: ['qalyubiyya', 'qaliubiya', 'kaliobeya', 'qaliobia', 'qalubia', 'qalioubia'],
  sharqia: ['sharkia', 'ash sharqiyah', 'el sharkia', 'sharqiyya', 'sharquia'],
  dakahlia: ['daqahliyya', 'dakahleya', 'ad daqahliyah', 'dakahlya', 'daqahlia'],
  gharbia: ['gharbiyya', 'el gharbia', 'al gharbiyah', 'gharbeya'],
  monufia: ['menofia', 'minufiyya', 'al minufiyah', 'menoufia', 'monofia', 'minufia', 'menufia'],
  beheira: ['buhayra', 'el beheira', 'al buhayrah', 'behera', 'beheria', 'bohaira'],
  'kafr-el-sheikh': ['kafr elsheikh', 'kafr al sheikh', 'kafrelsheikh', 'kafr alsheikh', 'kafr el shaikh', 'kafr el shikh'],
  damietta: ['dumyat', 'damyat', 'domyat'],
  'port-said': ['portsaid', 'bur said', 'port saeed', 'portsaeed'],
  ismailia: ['ismailiyah', 'ismailya', 'al ismailiyah', 'esmailia', 'ismaelia'],
  suez: ['as suways', 'al suways', 'sewis'],
  'north-sinai': ['shamal sinai', 'north sinai governorate', 'al arish'],
  'south-sinai': ['janub sinai', 'south sinai governorate', 'sharm el sheikh'],
  faiyum: ['fayoum', 'el fayoum', 'fayyum', 'al fayyum', 'fayum', 'faiyoum'],
  'beni-suef': ['bani suwayf', 'beni swef', 'bani sweif', 'benisuef', 'beni sweif', 'bani suef'],
  minya: ['el minia', 'minia', 'al minya', 'menia', 'el minya'],
  asyut: ['assiut', 'asyout', 'assyut', 'asyoot', 'assuit'],
  sohag: ['suhag', 'sohaj', 'sohage'],
  qena: ['qina', 'kena'],
  luxor: ['al uqsur', 'el uqsur', 'uqsur', 'luqsor'],
  aswan: ['assuan', 'aswan governorate'],
  'red-sea': ['al bahr al ahmar', 'bahr ahmar', 'red sea governorate', 'hurghada'],
  'new-valley': ['al wadi al jadid', 'wadi gedid', 'el wadi el gedid', 'new valley governorate', 'kharga'],
  matrouh: ['matruh', 'marsa matruh', 'mersa matrouh', 'matrooh', 'marsa matrouh'],
}

// Words that say "governorate" and nothing else.
const NOISE = new Set(['governorate', 'gov', 'province', 'محافظه', 'محافظة'])

/** Lower-case Latin, Arabic without diacritics/tatweel and with alef/ya/ta-marbuta variants merged, no "ال" article, no punctuation. */
export function normalizeName(raw: string): string {
  const s = raw
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '') // Latin accents
    .replace(/[ً-ٰٟـ]/g, '') // Arabic marks + tatweel
    .toLowerCase()
    .replace(/[أإآٱ]/g, 'ا')
    .replace(/ى/g, 'ي')
    .replace(/ة/g, 'ه')
    .replace(/[ؤ]/g, 'و')
    .replace(/[ئ]/g, 'ي')
    .replace(/[^a-z0-9؀-ۿ]+/g, ' ')
    .trim()
  return s
    .split(' ')
    .filter((w) => w && !NOISE.has(w))
    .map((w) => (/^ال.{1,}/.test(w) ? w.slice(2) : w))
    .join(' ')
}

const squash = (s: string) => s.replace(/ /g, '')

interface Entry {
  zone: ShippingZone
  keys: { spaced: string; squashed: string }[]
}

function entriesOf(zones: ShippingZone[]): Entry[] {
  return zones.map((zone) => ({
    zone,
    keys: [zone.name.en, zone.name.ar, ...(ALIASES[zone.code] ?? [])]
      .map(normalizeName)
      .filter(Boolean)
      .map((spaced) => ({ spaced, squashed: squash(spaced) })),
  }))
}

/** 0 = same name, 1 = starts with it, 2 = a word of the name starts with it, 3 = contains it, Infinity = no match. */
function score(e: Entry, q: string, qs: string): number {
  let best = Infinity
  for (const k of e.keys) {
    let s = Infinity
    if (k.squashed === qs) s = 0
    else if (k.squashed.startsWith(qs)) s = 1
    else if ((' ' + k.spaced).includes(' ' + q)) s = 2
    else if (qs.length >= 3 && k.squashed.includes(qs)) s = 3
    if (s < best) best = s
  }
  return best
}

function rank(zones: ShippingZone[], query: string): { zone: ShippingZone; score: number }[] {
  const q = normalizeName(query)
  const qs = squash(q)
  if (!qs) return []
  return entriesOf(zones)
    .map((e, i) => ({ zone: e.zone, score: score(e, q, qs), i }))
    .filter((r) => r.score < Infinity)
    .sort((a, b) => a.score - b.score || a.i - b.i)
    .map(({ zone, score }) => ({ zone, score }))
}

/** Zones to suggest for what is typed (best match first); nothing typed → no suggestions. */
export function searchZones(zones: ShippingZone[], query: string): ShippingZone[] {
  return rank(zones, query).map((r) => r.zone)
}

/**
 * The zone a text clearly means: the same name, or the only governorate it can still be while typing
 * ("Cai" → Cairo, "Sue" → Suez). Several candidates ("Sinai", "Q") or none → null, the shopper picks from the list.
 */
export function resolveZone(zones: ShippingZone[], text: string): ShippingZone | null {
  const r = rank(zones, text)
  if (r.length === 0) return null
  const best = r[0].score
  const top = r.filter((x) => x.score === best)
  if (top.length !== 1) return null
  return best === 0 || (best <= 2 && normalizeName(text).length >= 2) ? top[0].zone : null
}
