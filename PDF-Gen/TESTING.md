# thai-pdf-glyphshaping 0.1.0 — Tester Guide

Thank you for evaluating this library. This guide gets you to a rendered Thai PDF in about five minutes.

---

## What you are testing

`thai-pdf-glyphshaping` is an Apache-licensed Java library that adds proper OpenType Thai
shaping to Apache PDFBox / OpenHTMLToPDF. It handles:

- GSUB glyph substitution (contextual alternates, ligatures)
- GPOS mark placement (vowels and tone marks positioned precisely over/under base consonants)
- **Searchable output** — copy-paste and Ctrl+F work correctly in the generated PDF
- Both modern OpenType fonts (Noto Sans Thai, Tahoma) and legacy PUA fonts (TH Sarabun New)

The test document (`example/thai_test.html`) renders the same Thai corpus under all three
font models side-by-side, plus a page of mixed-font runs (different fonts within a single line).

---

## Prerequisites

| Tool | Minimum version |
|------|----------------|
| Java | 11+ |
| Maven | 3.6+ |
| Internet access | to resolve Maven dependencies on first run |

---

## Step 1 — Install the library jar to your local Maven repository

The library is not yet on Maven Central. Run this once from the folder containing the jars:

```bash
mvn install:install-file \
  -Dfile=thai-pdf-glyphshaping-0.1.0.jar \
  -DgroupId=io.github.de8tech \
  -DartifactId=thai-pdf-glyphshaping \
  -Dversion=0.1.0 \
  -Dpackaging=jar \
  -DgeneratePom=false \
  -DpomFile=/dev/null
```

On Windows (Command Prompt), replace `\` line continuations with `^`.

---

## Step 2 — Add your fonts

Place font files in `example/fonts/`:

| File | Required? | Where to get it |
|------|-----------|-----------------|
| `NotoSansThai-Regular.ttf` | **Yes** | [Google Fonts — Noto Sans Thai](https://fonts.google.com/noto/specimen/Noto+Sans+Thai) → Download family |
| `tahoma.ttf` | No | Bundled with Windows; copy from `C:\Windows\Fonts\tahoma.ttf` |
| `THSarabunNew.ttf` | No | Linux: `sudo apt install fonts-tlwg-sarabunnew` then copy from `/usr/share/fonts/`; or download from [f0nt.com](https://www.f0nt.com/release/th-sarabun-new/) |

The driver prints which fonts were registered and skips any that are missing.

---

## Step 3 — Run the example

```bash
cd example
mvn compile exec:java
```

This produces `example/thai_output.pdf`.

---

## Step 4 — What to check in the PDF

Open `thai_output.pdf` in **Adobe Acrobat** or **Foxit Reader** (preferred over browser PDF viewers for Thai text verification).

### Visual checks

| Check | What you should see |
|-------|---------------------|
| Above-base vowels (กิ กี กึ กื) | Vowel marks sit cleanly above the consonant, not overlapping the tone mark |
| Below-base vowels (กุ กู) | Mark hangs cleanly below the consonant |
| Tone over upper vowel (กี่ กี้ กี๊ กี๋) | Tone mark stacks above the upper vowel, not clashing |
| Sara Am words (น้ำ ลำ คำ) | The nikhahit (ํ) and sara aa (า) appear in the correct positions with the tone mark over the nikhahit |
| Mixed-font runs (Part 2) | All three fonts render consistently within the same line |

### Searchability checks (Ctrl+F in Adobe / Foxit)

| Search term | Expected result |
|-------------|----------------|
| `น้ำ` | Highlights the word น้ำ in the document |
| `สวัสดี` | Highlights สวัสดี |
| `ปิ้งปลา` | Highlights ปิ้งปลา |
| `ฤๅ` | Highlights ฤๅ (full-form character, should find it) |

### Copy-paste check

Select a Thai word in the PDF, copy, and paste into a text editor.
The pasted text should be the correct Unicode Thai sequence (not gibberish or empty).

---

## Logging

The library logs through SLF4J. The example is configured with `slf4j-simple` at `WARN` level,
so only degradation/fallback messages appear (none expected on a clean run with valid fonts).

If you see `SLF4J(W): No SLF4J providers were found` — this means the SLF4J backend is missing
from your classpath; the example `pom.xml` includes `slf4j-simple` which prevents this.

---

## Files in this package

```
thai-pdf-glyphshaping-0.1.0.jar         Main library jar
thai-pdf-glyphshaping-0.1.0-sources.jar Sources (attach in IDE for navigation)
thai-pdf-glyphshaping-0.1.0-javadoc.jar JavaDoc

example/
  pom.xml                                Maven project for the example driver
  thai_test.html                         Thai test document (Part 1: per-font; Part 2: mixed runs)
  fonts/                                 Place your .ttf files here
  src/main/java/ThaiPdfExample.java      Example driver source
```

---

## Feedback

Please send observations (visual regressions, copy-paste failures, search failures, or
unexpected console output) to **soranun@gmail.com** with:

- The PDF viewer name and version
- The operating system
- Which fonts you used
- A screenshot or description of the issue
