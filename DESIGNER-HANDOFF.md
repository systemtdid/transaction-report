# Transaction Report — HTML Template Design Spec (for the designer)

You're redesigning two HTML pages that the app turns into **PDF reports**. This
document tells you the rules to follow so your HTML drops straight into the
system and renders correctly.

> **TL;DR for a designer who doesn't know Java/Thymeleaf:**
> Design a normal static HTML+CSS page using the **sample values shown in the
> current files**. Keep the special `th:...` attributes where they are (they are
> the "data slots" the app fills in at runtime). Style everything else however
> you like. When in doubt, leave a `th:...` attribute alone.

---

## 1. What you're delivering

Two HTML files that **replace** the existing ones at the same paths:

| File | Becomes | Page size |
|------|---------|-----------|
| `src/main/resources/templates/report/daily_report.html`   | the **Daily** report PDF   | A4 portrait |
| `src/main/resources/templates/report/monthly_report.html` | the **Monthly** report PDF | A4 portrait |

The current versions of these files are your starting point — open them; they
already contain realistic sample text so you can see the layout.

There is also a reference design to match/improve on:
`Template/template_daily_report.pdf` and `Template/template_monthly_report.pdf`.

---

## 2. ⚠️ Which renderer? (decide BEFORE you start)

The HTML is converted to PDF by an engine, and the engine decides which CSS you
may use. There are two modes — **confirm with the TDID dev which one is active**,
because it changes the rules in Section 3:

- **Mode A — current engine (OpenHTMLToPDF).** Default today. Reliable, but only
  supports **older CSS** (roughly CSS 2.1). **No flexbox, no CSS grid.** Layout is
  done with tables / `display:table`. Follow the rules below as written.
- **Mode B — modern engine (headless Chrome).** The dev can switch to this first;
  then you can use **modern CSS** (flexbox, grid, web fonts) exactly as you would
  for "Print to PDF" in Chrome. If you want this, ask **before** designing — don't
  build a flexbox layout against Mode A, it will silently break.

Everything below is written for **Mode A** and is the safe baseline that works in
both modes.

---

## 3. Hard rules (break these and the PDF fails to generate)

1. **Well-formed XHTML.** Every tag must be closed. Self-close void elements:
   `<br/>`, `<img ... />`, `<meta ... />`, `<hr/>`. No unclosed `<td>`/`<div>`.
2. **No named HTML entities.** Use the character itself or a **numeric** entity:
   - `•` → `&#8226;`  •  non-breaking space → `&#160;`  •  `—` → `&#8212;`
   - ❌ `&bull;` `&nbsp;` `&mdash;` will crash the renderer.
3. **No JavaScript.** `<script>` is ignored. The PDF is static; all content comes
   from the data (Section 4) or is hard-coded text.
4. **CSS lives in a `<style>` block in `<head>`** (as it does now), or inline
   `style="..."`. Keep it in the file.
5. **Page setup via `@page`:** `@page { size: A4 portrait; margin: 15mm; }`
   Don't rely on fixed pixel page dimensions.
6. **Units:** use `pt`, `mm`, `%` (and `px` if needed). Colors as hex (`#003380`).
7. **Fonts:** you may only use the families the app provides:
   **`Tahoma`** and **`Noto Sans Thai`** (Thai text *must* fall back to one of
   these or it won't shape correctly). To add a new font, the dev must install it
   server-side first — ask before using anything else.
8. **Mode A layout = tables / `display:table`/`table-cell`** for columns and
   side-by-side blocks. **No `display:flex` / `display:grid`.**
9. **Logos/images:** embed as a **base64 data URI** (`<img src="data:image/png;base64,...."/>`)
   so nothing depends on an external file. Provide assets to the dev if you'd
   rather they be bundled.

---

## 4. Data slots — the dynamic parts you must preserve

The app injects live data through `th:...` attributes (Thymeleaf). The visible
text inside those elements (e.g. `01 January 2026`, `ORG_ID`, `0`) is just dummy
preview text — at runtime it's **replaced**. You can move a `th:...` onto whatever
element your design uses, but the **attribute and its `${...}` expression must
stay**. Three kinds you'll see:

- `th:text="${...}"` → replaces the element's text with a value. **Keep it.**
- `th:each="row : ${data.rows}"` → repeats that element once per data row (the
  table grows to N rows). **Keep it on the repeating row.**
- `th:if="${...}"` → shows a block only when a condition is true. **Keep it on
  the conditional block.**

### 4a. DAILY report — available fields (root object: `data`)

| Slot | Expression (keep as-is) | Meaning | Format shown |
|------|--------------------------|---------|--------------|
| Org name | `${data.org.displayName}` | e.g. "Government Saving Bank" | text |
| Org ID | `${data.org.orgId}` | e.g. "0994000164891" | text |
| Period | `${#temporals.format(data.period.atDay(1), 'MMMM, yyyy')}` | report month | "January, 2026" |
| **Rows** (repeat) | `th:each="row, stat : ${data.rows}"` | one line **per day** (28–31 rows) | — |
| → date | `${#temporals.format(row.date, 'dd MMMM yyyy')}` | the day | "01 January 2026" |
| → count | `${#numbers.formatInteger(row.count, 1, 'COMMA')}` | transactions that day | "1,234" |
| Total | `${#numbers.formatInteger(data.total, 1, 'COMMA')}` | sum of all days | "12,345" |

### 4b. MONTHLY report — available fields (root object: `data`)

| Slot | Expression (keep as-is) | Meaning | Format shown |
|------|--------------------------|---------|--------------|
| Org name | `${data.org.displayName}` | org | text |
| Org ID | `${data.org.orgId}` | org id | text |
| Period | `${#temporals.format(data.period.atDay(1), 'MMMM yyyy')}` | report month | "May 2026" |
| **Has fee table?** | `th:if="${data.org.hasFeeSchedule()}"` | the whole tier table shows only for billing orgs | — |
| **Tier rows** (repeat) | `th:each="line, stat : ${data.lines}"` | one line **per fee tier** (up to 5) | — |
| → tier range | `${line.tier.displayRange()}` | e.g. "1 - 720,000" or "2,880,001 ++" | text |
| → rate | `${#numbers.formatDecimal(line.tier.rate(), 1, 'COMMA', 2, 'POINT')}` | Baht/txn | "0.70" |
| → usage | `${#numbers.formatInteger(line.usageCount, 1, 'COMMA')}` | txns in tier | "720,000" |
| → fee | `${#numbers.formatDecimal(line.fee, 1, 'COMMA', 2, 'POINT')}` | Baht | "504,000.00" |
| Total usage | `${#numbers.formatInteger(data.totalUsage, 1, 'COMMA')}` | total txns | "1,000,000" |
| Computed fee | `${#numbers.formatDecimal(data.computedFee, 1, 'COMMA', 2, 'POINT')}` | sum of tier fees | "650,000.00" |
| Accumulated | `${#numbers.formatInteger(data.accumulatedTransactions, 1, 'COMMA')}` | running total | "1,000,000" |
| Billed fee | `${#numbers.formatDecimal(data.billedFee, 1, 'COMMA', 2, 'POINT')}` | amount actually billed | "650,000.00" |
| **Below minimum?** | `th:if="${data.belowMinimum}"` | shows the remark block only when usage < contractual minimum | — |
| Remark | `${data.remark}` | the remark sentence | text |
| Run date | `${#temporals.format(data.runStamp, 'dd/MM/yyyy')}` | when generated | "24/06/2026" |
| Run time | `${#temporals.format(data.runStamp, 'HH:mm:ss')}` | when generated | "11:47:06" |

> Notes for layout decisions:
> - The **daily table is variable length** (one row per day of the month). Don't
>   give it a fixed height; let it flow and page-break naturally across A4 pages.
> - The **monthly tier table only appears for some orgs** (those with a fee
>   schedule). Design the page so it still looks right *without* that table.
> - **Thai text appears** (e.g. "บริษัท ไทย ดิจิทัล ไอดี จำกัด"). Make sure your
>   font choices keep it on `Tahoma`/`Noto Sans Thai`.

---

## 5. Simplest workflow (recommended)

You don't have to understand Thymeleaf. Easiest split of work:

1. You design a **static HTML+CSS** page that looks the way you want, using the
   sample values from the current files as placeholder content.
2. Mark (a comment, a highlight, or just a note) **which pieces of text are
   dynamic** — basically the rows in the tables of Section 4.
3. Hand it back; the TDID dev re-applies the `th:...` data slots into your markup.

If you *are* comfortable keeping the `th:...` attributes yourself, even better —
follow Section 4 and deliver the final files directly.

## 6. How it gets tested

The dev runs the app and downloads the PDF to check it. If you're on **Mode B**
(Chrome engine), your own "Print → Save as PDF" from Chrome will look essentially
identical to the final output, so you can self-check. On **Mode A**, the dev does
the final PDF check.
