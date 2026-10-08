# Prompts to paste into Claude Code (one at a time)

## 1 — Plan
Read CLAUDE.md, every screenshot in design/screens and both reference files in design/. Don't write code yet. Propose the folder structure, component list, routes, how you'll port the hero canvas animation and the countdown, and how you'll handle EN/AR + RTL. Ask me anything that's unclear.

## 2 — Scaffold
Create the React + Vite + TypeScript project as planned: router (/en, /ar, /:lang/product/:slug), global CSS tokens and fonts, i18n with all copy taken from the two reference files, mock product + drop data, bag store with localStorage. Move assets/ into public/assets/. Run it and fix any errors.

## 3 — Home page
Build the home page exactly like the screenshots: announcement bar with countdown, sticky header (VOID wordmark only, no logo image), phone menu with search, hero (black hole + glows + canvas stars/particles ported from the reference script), product grid (4 columns desktop, 2 on phone), footer (links / © 2026 VOID / Instagram + TikTok icons). Copy the CSS values and keyframes from design/store-en.reference.html. Check each section at 390px and 1440px against the screenshots.

## 4 — Product page
Build /:lang/product/:slug as in the screenshots: back link, gallery (2×2 desktop, swipe + dots on phone), sizes, quantity, add to bag with the no-size warning and toast, accordions, "More from Drop 01", sticky bottom bar on phone. Product cards on the home page link here.

## 5 — Arabic + polish
Verify the whole site in Arabic (RTL), reduced motion, keyboard focus, no horizontal scroll at 390/768/1440, and Lighthouse mobile performance. Fix what you find and list everything still marked [PLACEHOLDER].
