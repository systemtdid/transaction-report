# Transaction Report Web Application — Architecture & Implementation Spec

> **Purpose of this document.** This is the *context cache* for the developer agent that
> will implement the system. It captures everything reverse-engineered from the existing
> shell scripts and PDF templates, plus the full target design (Spring Boot 3.x / Java 21).
> It deliberately contains **no production Java method bodies** — only structure, signatures,
> SQL, configuration, and a step-by-step build order. Anything marked **[VERIFY]** is an
> inference from a binary artifact that the implementer must confirm against the real
> template / database before shipping.

---

## 1. Goal

A small Spring Boot web application that lets an operator:

1. Enter an **Organization ID** (`orgId`).
2. Choose a report type: **Daily** or **Monthly**.
3. Click **Download** and receive a generated, Thai-shaped, searchable **PDF**.

The reports reproduce the two existing PDF layouts (`Template/template_daily_report.pdf`,
`Template/template_monthly_report.pdf`) but are generated dynamically from live data in the
`fmsv_db` MySQL database, replacing the current manual `bash + mysql` scripts.

---

## 2. Source material analysis (reverse-engineered)

### 2.1 `manual-script/daily_report_count.sh`
- **State:** The committed file is **truncated** (CRLF line endings, only 22 lines, begins
  mid-`for`-loop). The header — credentials, date range, the per-day SQL `COUNT` query, and
  loop setup — is **missing**. **[VERIFY]** recover the full script or confirm intent.
- **Observable behaviour (from the surviving tail + the daily PDF):**
  - Iterates day-by-day across a target month.
  - For each day, runs a `COUNT` of transactions and prints a CSV row:
    `"<DD> <Month> <YYYY>,<count>"` (e.g. `01 March 2026,45`).
  - Accumulates a running `TOTAL` and prints `Total Transaction,<TOTAL>`.
  - Writes to a `$CSV_FILE`, removes a `$TMP_FILE`, prints a success banner.
- **Inferred query (mirrors the monthly script):**
  ```sql
  SELECT COUNT(tranID) FROM transactions
  WHERE orgID = ? AND tranStatus = 'SUCCESS'
    AND processedTime BETWEEN ? AND ?;   -- one [00:00:00 .. 23:59:59] window per day
  ```

### 2.2 `manual-script/Monthly_report_count.sh`
- Sets MySQL creds inline (`root` / hardcoded password) against DB `fmsv_db`. **(Security debt — see §13.)**
- Computes previous-month range:
  `START = first day of last month 00:00:00`, `END = day-31 of last month 23:59:59`.
  **[VERIFY]** day-`31` is a bug for short months; use month-end correctly in Java.
- Runs four `COUNT(tranID)` queries, all filtered `tranStatus='SUCCESS'` + `processedTime BETWEEN START AND END`:

  | Label    | `orgID`         | Extra filter              |
  |----------|-----------------|---------------------------|
  | KTB      | `0107537000882` | —                         |
  | TMC      | `0994000953534` | —                         |
  | DHIP     | `0107556000051` | —                         |
  | TIPxGSB  | `0994000164891` | `gatewayID = 'TIPxGSB01'` |

- Writes two summary `.txt` files in `/tmp`. No fee calculation in the script itself.

### 2.3 Database schema (inferred — **[VERIFY]** against `fmsv_db`)
- Table **`transactions`** with at least:
  `tranID`, `orgID` (varchar), `tranStatus` (`'SUCCESS'` is the billable state),
  `processedTime` (datetime), `gatewayID` (varchar).

### 2.4 Daily PDF template (`Template/template_daily_report.pdf`)
- **Flat PDF, A4 portrait (595.32 × 841.92 pt). No AcroForm fields** — cannot be "filled".
- Decoded text content:
  - Title: **"Transaction Summary Report"**
  - Subtitle: **"For Government Saving Bank"** (GSB → `orgID 0994000164891`)
  - **"As of March, 2026"**
  - **"Organization: 0994000164891"**
  - Two-column table header: **Date | No. of Transaction**
  - Per-day rows: `01 March 2026 | 45`, `02 March 2026 | 199`, `03 March 2026 | 65`, …
  - A logo image (one `XObject`).
- Maps 1:1 to the daily script's CSV output.

### 2.5 Monthly PDF template (`Template/template_monthly_report.pdf`)
- **Flat PDF, A4 (595 × 842 pt). AcroForm present but `/Fields[]` empty — not fillable.**
- Bilingual (Thai + English), uses **CordiaNew / CordiaNew-Bold** fonts.
- It is a **billing / invoice "Transaction Summary Report of Signing Service"**, not a raw count.
- Decoded structure:
  - **TDID letterhead** (English + Thai): `319 25th Floor, Room 10-11, Chamchuri Square
    Building, Phayathai Road, Pathumwan, Bangkok, 10330`, `URL: www.thaidigitalid.com`,
    phone `02 029 0290`, fax `02 029 0293`, `support@thaidigitalid.com`.
  - Title: **"A Transaction Summary Report of Signing Service for <Customer Name>"**
    (sample customer: *Prudential Life Assurance (Thailand) Public Company Limited*).
  - **"As of <Month> <Year>"** (sample: December 2023).
  - **Tiered fee table** — columns: `Transaction Tier | Transaction Fee Rate(Baht) |
    No. of usage Transaction | Fee(Baht)`:

    | Tier | Range (transactions) | Rate (Baht) **[VERIFY ordering]** |
    |------|----------------------|-----------------------------------|
    | 1 | `1 – 720,000`           | **0.70** ✅ verified                |
    | 2 | `720,001 – 1,440,000`   | 0.65 / 0.60 *(draw-order ambiguous)* |
    | 3 | `1,440,001 – 2,160,000` | 0.60 / 0.65                         |
    | 4 | `2,160,001 – 2,880,000` | 0.55 / 0.50                         |
    | 5 | `2,880,001 ++`          | 0.50 / 0.55                         |

    > The decoded raw rate sequence is `0.60, 0.70, 0.65, 0.50, 0.55` in PDF draw order,
    > which is **not** guaranteed to be tier order. **Tier-1 = 0.70 is confirmed** because the
    > sample shows tier-1 usage `89,741` → fee `62,818.70`, and `89,741 × 0.70 = 62,818.70`.
    > The implementer must read the rendered template to fix tiers 2–5, then encode the
    > schedule as **per-org configuration** (§5.3), not hardcode it.
  - **Per-tier fee = usage_count × rate**; tiers fill from tier 1 upward.
  - **Totals row** (`Total`), `Fee(Baht)` total.
  - **Minimum-commitment logic [VERIFY]:** the template also shows
    `Total Fee Amount (Baht): 0.00` with
    `Remark: Number of usage transaction has not exceeded the minimum limit`, and
    `Accumulated Transaction (<Mon> <Year>): = 436,247`. Interpretation: if the customer's
    **accumulated** usage has not crossed a contractual minimum, the **billed** amount is
    `0.00` (overriding the computed tier fee) and the remark is shown.
  - **Run Date / Run Time** stamp (e.g. `Run Date: 04/01/2023`, `Run Time: 14:43:36`).
  - Footer repeats the TDID address block.
  - Lists several bank/customer names (UOB Thai, TMBThanachart Bank, TMBThanachart Broker,
    CIMB Thai, Prudential…), `File Type: PDF`, `Organization: <…>`.

### 2.6 PDF engine (`PDF-Gen/`)
- Library **`thai-pdf-glyphshaping` 0.1.0** (`io.github.de8tech`), Apache-licensed,
  shipped as a local jar (`PDF-Gen/thai-pdf-glyphshaping-0.1.0.jar`). **Not on Maven Central.**
- It augments **OpenHTMLToPDF + Apache PDFBox** with OpenType Thai GSUB/GPOS shaping and
  produces **searchable** PDFs.
- Canonical usage (from `PDF-Gen/example/src/main/java/ThaiPdfExample.java`):
  ```text
  PdfRendererBuilder builder = new PdfRendererBuilder();
  builder.useFastMode();
  builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
  builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
  builder.withHtmlContent(html, baseUri);   // or withFile / withW3cDocument
  builder.toStream(os);
  try (ShapedPdfBoxRenderer renderer = ShapedPdfBoxRenderer.build(builder)) {
      renderer.addShapingFont(notoFile, "Noto Sans Thai", true); // true = Sara-Am decompose
      renderer.addShapingFont(tahomaFile, "Tahoma", true);       // optional
      renderer.addShapingFont(sarabunFile, "TH Sarabun New");    // legacy PUA, no decompose
      renderer.layout();
      renderer.createPDF();
  }
  ```
- **Conclusion:** the report generation path is **HTML → PDF**. We will NOT fill the existing
  flat PDFs. We recreate each layout as an HTML/Thymeleaf template, render it through this
  library, and use the original PDFs only as a **visual reference** for fonts, spacing, and
  wording.
- Font registration: `addShapingFont(File, family)` / `addShapingFont(File, family, boolean
  decompose)`. **[VERIFY]** the exact public method signatures by attaching the bundled
  `*-sources.jar` / `*-javadoc.jar` in the IDE.

---

## 3. Technology stack

| Concern         | Choice                                                            |
|-----------------|-------------------------------------------------------------------|
| Language        | **Java 21** (LTS)                                                  |
| Framework       | **Spring Boot 3.3.x** (Web MVC)                                    |
| Build           | **Maven** (the existing assets use Maven; library install is Maven) |
| Web UI          | **Thymeleaf** (form page) + **Thymeleaf** (HTML report templates)  |
| Data access     | **Spring JDBC (`JdbcTemplate`)** — read-only counts, no ORM needed |
| DB driver       | **mysql-connector-j**                                              |
| PDF             | **thai-pdf-glyphshaping 0.1.0** + OpenHTMLToPDF + PDFBox + ICU4J   |
| Logging         | SLF4J/Logback (Spring Boot default)                               |
| Packaging       | Executable fat JAR; fonts shipped under `src/main/resources/fonts` |

### 3.1 Local jar prerequisite
Before the first build, install the library into the local Maven repo (see
`PDF-Gen/TESTING.md`):
```bash
mvn install:install-file \
  -Dfile=PDF-Gen/thai-pdf-glyphshaping-0.1.0.jar \
  -DgroupId=io.github.de8tech -DartifactId=thai-pdf-glyphshaping \
  -Dversion=0.1.0 -Dpackaging=jar -DgeneratePom=false -DpomFile=/dev/null
```
**[VERIFY]** whether the library's transitive dependencies (OpenHTMLToPDF, PDFBox, ICU4J)
resolve via its POM. Since it was installed with `-DgeneratePom=false`, there is **no POM**, so
**transitive deps will NOT resolve automatically** — they must be declared explicitly in our
`pom.xml` (see §3.2). Pin versions to whatever the jar was compiled against
(inspect the jar's `MANIFEST.MF` / classes; **[VERIFY]** — OpenHTMLToPDF ~1.0.10, PDFBox ~2.0.x
are the likely targets).

### 3.2 `pom.xml` dependency set (reference)
```xml
<project>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>           <!-- [VERIFY latest 3.3.x] -->
  </parent>
  <properties>
    <java.version>21</java.version>
    <openhtmltopdf.version>1.0.10</openhtmltopdf.version> <!-- [VERIFY] -->
  </properties>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-thymeleaf</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jdbc</artifactId></dependency>
    <dependency><groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>

    <!-- PDF: local lib + its (non-resolving) transitives, declared explicitly -->
    <dependency><groupId>io.github.de8tech</groupId>
      <artifactId>thai-pdf-glyphshaping</artifactId><version>0.1.0</version></dependency>
    <dependency><groupId>com.openhtmltopdf</groupId>
      <artifactId>openhtmltopdf-core</artifactId><version>${openhtmltopdf.version}</version></dependency>
    <dependency><groupId>com.openhtmltopdf</groupId>
      <artifactId>openhtmltopdf-pdfbox</artifactId><version>${openhtmltopdf.version}</version></dependency>
    <dependency><groupId>com.openhtmltopdf</groupId>
      <artifactId>openhtmltopdf-rtl-support</artifactId><version>${openhtmltopdf.version}</version></dependency>

    <dependency><groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
```
> `openhtmltopdf-rtl-support` provides the `ICUBidiSplitter` / `ICUBidiReorderer` classes
> (and pulls ICU4J) used in the example.

---

## 4. Project structure

```
transaction-report/
├─ pom.xml
├─ ARCHITECTURE.md                       (this file)
├─ src/main/java/com/tdid/txreport/
│  ├─ TransactionReportApplication.java        (@SpringBootApplication entrypoint)
│  ├─ web/
│  │  └─ ReportController.java                  (UI form + download endpoints)
│  ├─ service/
│  │  ├─ ReportService.java                     (facade: dispatch DAILY/MONTHLY)
│  │  ├─ DailyReportService.java                (adapts daily_report_count.sh)
│  │  ├─ MonthlyReportService.java              (adapts Monthly_report_count.sh)
│  │  └─ FeeCalculator.java                     (tiered billing engine)
│  ├─ pdf/
│  │  ├─ PdfRenderService.java                  (HTML -> PDF via thai-pdf-glyphshaping)
│  │  └─ HtmlReportRenderer.java                (Thymeleaf model -> HTML string)
│  ├─ repository/
│  │  └─ TransactionRepository.java             (parameterized COUNT queries)
│  ├─ domain/
│  │  ├─ ReportType.java                        (enum DAILY, MONTHLY)
│  │  ├─ OrgProfile.java                        (orgId, displayName, gatewayId, FeeSchedule)
│  │  ├─ FeeSchedule.java / FeeTier.java        (tier ranges + rates + minimum)
│  │  ├─ DailyReportData.java                   (rows + total + header meta)
│  │  ├─ DailyCount.java                        (date, count)
│  │  └─ MonthlyReportData.java                 (tier lines, totals, run stamp, remark)
│  ├─ config/
│  │  ├─ OrgRegistryProperties.java             (@ConfigurationProperties org list)
│  │  ├─ FontProperties.java                    (font file locations)
│  │  └─ DataSourceConfig.java                  (read-only DataSource, if customizing)
│  └─ exception/
│     ├─ UnknownOrgException.java
│     └─ GlobalExceptionHandler.java            (@ControllerAdvice)
├─ src/main/resources/
│  ├─ application.yml
│  ├─ templates/
│  │  ├─ index.html                             (the input form UI)
│  │  └─ report/
│  │     ├─ daily_report.html                   (daily layout)
│  │     └─ monthly_report.html                 (monthly billing layout)
│  ├─ static/css/report.css                     (shared print CSS, A4 @page)
│  └─ fonts/
│     ├─ NotoSansThai-Regular.ttf               (required)
│     ├─ THSarabunNew.ttf                        (optional)
│     └─ CordiaNew*.ttf                          (to match monthly template look) [VERIFY licensing]
└─ src/test/java/com/tdid/txreport/...          (unit + slice tests)
```

---

## 5. Configuration

### 5.1 `application.yml` (reference)
```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:3306/fmsv_db?serverTimezone=Asia/Bangkok
    username: ${DB_USER:reporting_ro}        # NOT root — read-only account (§13)
    password: ${DB_PASSWORD}                 # injected via env / secret, never committed
    hikari:
      read-only: true
      maximum-pool-size: 5

txreport:
  fonts:
    base-path: classpath:/fonts/
    noto: NotoSansThai-Regular.ttf
    sarabun: THSarabunNew.ttf
  default-zone: Asia/Bangkok
  orgs:                                       # the org registry (§5.3)
    - org-id: "0994000164891"
      display-name: "Government Saving Bank"
      gateway-id: "TIPxGSB01"
    - org-id: "0107537000882"
      display-name: "Krung Thai Bank"
    - org-id: "0994000953534"
      display-name: "TMC"
    - org-id: "0107556000051"
      display-name: "DHIP"
      fee-schedule:
        minimum-transactions: 720000         # [VERIFY] contractual minimum
        tiers:
          - { from: 1,       to: 720000,  rate: 0.70 }   # verified
          - { from: 720001,  to: 1440000, rate: 0.65 }   # [VERIFY]
          - { from: 1440001, to: 2160000, rate: 0.60 }   # [VERIFY]
          - { from: 2160001, to: 2880000, rate: 0.55 }   # [VERIFY]
          - { from: 2880001, to: null,    rate: 0.50 }   # [VERIFY]
```

### 5.2 Font handling
- Fonts must be available as `File` objects at render time. `addShapingFont` takes a `File`
  (per the example). Since fonts ship inside the jar, on startup copy each classpath font to a
  temp file once (cache the `File`), or extract to a known runtime dir. **[VERIFY]** whether
  the library also exposes a stream/`byte[]` overload to avoid temp extraction.
- The monthly template uses **CordiaNew**; to match it visually, register CordiaNew. If
  licensing prevents bundling, fall back to Noto Sans Thai and accept a visual delta.
  **[VERIFY licensing.]**

### 5.3 Org registry
`OrgRegistryProperties` binds `txreport.orgs`. `OrgProfile` carries:
`orgId`, `displayName`, optional `gatewayId` (extra SQL filter), optional `FeeSchedule`
(required only for orgs that get a monthly billing report). Lookups by `orgId`; unknown
`orgId` → `UnknownOrgException` → HTTP 404/400.

---

## 6. Domain model (responsibilities, not code)

- **`ReportType`** — `enum { DAILY, MONTHLY }`.
- **`OrgProfile`** — record: `String orgId, String displayName, String gatewayId,
  FeeSchedule feeSchedule`.
- **`FeeSchedule`** — `long minimumTransactions, List<FeeTier> tiers`; helper to locate the
  tier for a cumulative position.
- **`FeeTier`** — `long from, Long to (null = open-ended), BigDecimal rate`.
- **`DailyCount`** — `LocalDate date, long count`.
- **`DailyReportData`** — `OrgProfile org, YearMonth period, List<DailyCount> rows,
  long total`. Rows include every calendar day of the period (zero-filled).
- **`MonthlyTierLine`** — `FeeTier tier, long usageCount, BigDecimal fee`.
- **`MonthlyReportData`** — `OrgProfile org, YearMonth period, List<MonthlyTierLine> lines,
  long totalUsage, BigDecimal computedFee, BigDecimal billedFee, boolean belowMinimum,
  long accumulatedTransactions, String remark, LocalDateTime runStamp`.

> **Money:** use `BigDecimal` with scale 2, `RoundingMode.HALF_UP`, for all fee math.

---

## 7. Data access layer

**`TransactionRepository`** (wraps `JdbcTemplate`) — all queries **parameterized** (fixes the
SQL-injection debt of the scripts):

```text
long countSuccess(String orgId, String gatewayId,        // gatewayId nullable
                  LocalDateTime startInclusive, LocalDateTime endInclusive)

List<DailyCount> countSuccessByDay(String orgId, String gatewayId,
                  LocalDate monthStart, LocalDate monthEndInclusive)
```

- `countSuccess` SQL:
  ```sql
  SELECT COUNT(tranID) FROM transactions
  WHERE orgID = :orgId
    AND tranStatus = 'SUCCESS'
    AND processedTime BETWEEN :start AND :end
    AND (:gatewayId IS NULL OR gatewayID = :gatewayId)
  ```
- `countSuccessByDay` SQL (replaces the per-day loop in the daily script with **one**
  round-trip; the service zero-fills missing days):
  ```sql
  SELECT DATE(processedTime) AS d, COUNT(tranID) AS c
  FROM transactions
  WHERE orgID = :orgId
    AND tranStatus = 'SUCCESS'
    AND processedTime >= :start AND processedTime < :endExclusive
    AND (:gatewayId IS NULL OR gatewayID = :gatewayId)
  GROUP BY DATE(processedTime)
  ORDER BY d
  ```
  > Prefer a half-open range `[start, endExclusive)` over `BETWEEN … 23:59:59` to avoid the
  > sub-second / leap edge the scripts ignore, and to fix the day-`31` month-end bug.
- For monthly accumulated transactions (the `Accumulated Transaction (…)` figure), an
  additional aggregate over the contract-to-date window is needed. **[VERIFY]** the exact
  accumulation window (calendar year? contract year? rolling?).

---

## 8. Service layer (mapping scripts → Spring)

### 8.1 `ReportService` (facade)
```text
byte[] generate(String orgId, ReportType type, YearMonth period)
```
- Resolves `OrgProfile` from the registry (404 if unknown).
- Dispatches to `DailyReportService` / `MonthlyReportService`.
- Returns the rendered PDF bytes (delegating to `PdfRenderService`).
- Decides `period` default: DAILY → current month (or user-selected); MONTHLY →
  previous month (matches script `date --date="1 month ago"`).

### 8.2 `DailyReportService` (← `daily_report_count.sh`)
```text
DailyReportData build(OrgProfile org, YearMonth period)
```
Steps:
1. Compute `monthStart = period.atDay(1)`, `endExclusive = period.plusMonths(1).atDay(1)`.
2. `repo.countSuccessByDay(org.orgId, org.gatewayId, …)`.
3. Zero-fill every day of the month into an ordered `List<DailyCount>`.
4. Sum to `total` (the script's `Total Transaction`).
5. Return `DailyReportData` with header meta (org display name, "As of <Month>, <Year>").

> Script-behaviour parity: CSV row `"DD Month YYYY,count"` becomes a table row; the trailing
> `Total Transaction,TOTAL` becomes the table footer. The temp-file/`rm` plumbing is dropped.

### 8.3 `MonthlyReportService` (← `Monthly_report_count.sh` + monthly template billing)
```text
MonthlyReportData build(OrgProfile org, YearMonth period)
```
Steps:
1. Range = full previous (selected) month, half-open.
2. `usage = repo.countSuccess(org.orgId, org.gatewayId, …)`.
3. `accumulated = repo.countSuccess(…, accumulationWindow)`  **[VERIFY window]**.
4. `FeeCalculator.calculate(org.feeSchedule, usage)` → tier lines + computed fee.
5. Apply **minimum-commitment rule**: if `accumulated < feeSchedule.minimumTransactions`
   → `billedFee = 0.00`, `belowMinimum = true`,
   `remark = "Number of usage transaction has not exceeded the minimum limit"`;
   else `billedFee = computedFee`, no remark. **[VERIFY exact rule.]**
6. Stamp `runStamp = LocalDateTime.now(zone)` (template's Run Date / Run Time).
7. Return `MonthlyReportData`.

> The original script handled four orgs in one run and wrote text files. Here each request is
> single-org and returns a PDF; the multi-org batch behaviour, if still needed, becomes a
> simple loop over the registry (out of scope for the web flow but trivial to add later).

### 8.4 `FeeCalculator` (tiered billing engine)
```text
List<MonthlyTierLine> calculate(FeeSchedule schedule, long usage)
BigDecimal totalFee(List<MonthlyTierLine> lines)
```
- Walk tiers in order; for each, `tierUsage = overlap(usage-so-far, tier.range)`;
  `fee = tierUsage × tier.rate`. Fill from tier 1 upward (matches the sample where all
  89,741 land in tier 1 and the rest are 0).
- All `BigDecimal`, scale 2, HALF_UP.
- **Verified invariant:** tier-1 rate `0.70`, `89,741 → 62,818.70`.

> **Superseded by the dynamic Billing Engine — see §8.5.** `FeeCalculator`,
> `FeeSchedule`, and the old `domain.FeeTier (from,to,rate)` describe the original
> hardcoded, single-model billing. The Billing Engine generalises this into a
> data-driven engine supporting five pricing logics. New work targets §8.5.

---

## 8.5 Dynamic Billing Engine (data-driven, supersedes §8.4)

**Goal:** price each organisation's monthly usage from **declarative configuration**
rather than hardcoded tiers, so new pricing models are added by editing data — not
code. One engine must satisfy **five composable business logics**:

1. **Progressive Tiered Pricing** — usage fills tiers in order; each tier charges
   `unitsInTier × rate` (tax-bracket style).
2. **Free Tier (Zero Rating)** — a tier whose `rate = 0.00`; the leading N units cost
   nothing (e.g. Tokio Marine: first 50,000 free).
3. **Minimum Fee Guarantee** — bill `max(computedFee, minimumFee)`; the invoice never
   drops below a floor (e.g. Muang Thai: ≥ 42,000.00).
4. **Fixed Monthly Fee** — a flat platform fee **added** to the usage-based fee
   (e.g. Krungthai Bank: +30,000.00/month). *(Additive — see Open Questions.)*
5. **Minimum Limit Waive** — if usage has not reached the contractual minimum, the
   whole invoice is waived to `0.00` (e.g. Prudential, Dhipaya). This is the
   generalised form of the existing "below-minimum" rule in §8.3.

These compose: a single org config may combine several (the engine evaluates all,
in the fixed order below). New code lives in a new package
**`com.tdid.txreport.billing`** (the new `billing.FeeTier` replaces the old
`domain.FeeTier`; `FeeSchedule`/`FeeCalculator` are retired during migration).

### 8.5.1 Data models (Java 21 records)

```java
// One pricing band. maxCapacity = WIDTH of the band (units it can hold),
// NOT a cumulative ceiling. The final tier has maxCapacity == null (unbounded).
public record FeeTier(int tierNumber, Long maxCapacity, BigDecimal rate) {
    public boolean isUnbounded() { return maxCapacity == null; }
    public boolean isFree()      { return rate.signum() == 0; }   // zero rating
}

// Declarative per-org pricing. Any of minimumFee / fixedMonthlyFee may be null
// (= logic not applied). Tiers are ordered tier 1 → N.
public record OrgBillingConfig(
        String orgId,
        String orgName,
        BigDecimal minimumFee,        // nullable → Minimum Fee Guarantee
        BigDecimal fixedMonthlyFee,   // nullable → Fixed Monthly Fee
        boolean waiveIfBelowMinimum,  //         → Minimum Limit Waive
        List<FeeTier> tiers) {

    public boolean hasMinimumFee()      { return minimumFee != null; }
    public boolean hasFixedMonthlyFee() { return fixedMonthlyFee != null; }

    // Threshold for the waive rule. ASSUMPTION: the contractual minimum equals
    // the first tier's capacity (see Open Questions §8.5.5).
    public long minimumLimit() {
        return (tiers.isEmpty() || tiers.get(0).maxCapacity() == null)
                ? 0L : tiers.get(0).maxCapacity();
    }
}

// Per-tier outcome → maps 1:1 to the monthly report's tier-table rows.
public record TierCharge(int tierNumber, Long maxCapacity,
                         BigDecimal rate, long unitsInTier, BigDecimal charge) {}

// Full billing outcome for one org/period.
public record BillingResult(
        String orgId,
        long usage,
        List<TierCharge> tierBreakdown,
        BigDecimal tieredFee,        // Σ tier charges
        BigDecimal fixedMonthlyFee,  // applied fixed fee (0.00 if none)
        BigDecimal computedFee,      // tieredFee + fixedMonthlyFee (pre-guarantee/waive)
        BigDecimal billedFee,        // FINAL amount invoiced
        boolean minimumFeeApplied,   // true if the guarantee floor raised the fee
        boolean waived,              // true if the minimum-limit waive zeroed it
        String remark) {}            // e.g. below-minimum message, else null
```

> **Money:** every `BigDecimal` is scale 2, `RoundingMode.HALF_UP` (consistent with
> §6). Rates may carry more precision (e.g. KTB `0.4280`); only the *charge* is
> rounded to scale 2.

### 8.5.2 `BillingEngineService` (the engine)

```java
BillingResult calculate(OrgBillingConfig config, long usage)
```

**Deterministic pipeline** (every logic evaluated, in this order):

```text
1. TIERED   tieredFee = 0; remaining = usage
            for each tier (1..N):
                units = tier.isUnbounded() ? remaining
                                           : min(remaining, tier.maxCapacity)
                charge = round2(units × tier.rate)     // Free Tier ⇒ rate 0 ⇒ 0.00
                record TierCharge(...); tieredFee += charge; remaining -= units
                if remaining == 0 break
2. FIXED    computedFee = tieredFee + (config.fixedMonthlyFee ?: 0)      // logic 4
3. FLOOR    guaranteed  = config.hasMinimumFee()                         // logic 3
                          ? computedFee.max(config.minimumFee) : computedFee
            minimumFeeApplied = guaranteed > computedFee
4. WAIVE    if config.waiveIfBelowMinimum && usage < config.minimumLimit(): // logic 5
                billedFee = 0.00; waived = true; remark = BELOW_MIN_REMARK
            else:
                billedFee = guaranteed; waived = false
```

- **Progressive Tiered (1)** and **Free Tier (2)** are the same loop — a free tier is
  simply `rate = 0`, so no special-casing is needed.
- **Waive (4) wins** over the guarantee/fixed fee (final override → `0.00`). For the
  eight target orgs no org combines *waive* with *minimumFee/fixedMonthlyFee*, so the
  ordering is unambiguous; defining it keeps the engine deterministic for future configs.
- The service is **stateless** and pure (no DB, no clock) → trivially unit-testable;
  feed `(config, usage)` and assert `BillingResult`. `MonthlyReportService` (§8.3)
  becomes a thin caller: resolve config → get `usage` from the repo → `calculate` →
  map `tierBreakdown`/`billedFee`/`remark` onto `MonthlyReportData`.

### 8.5.3 Data initialization (load configs into memory)

Configs are loaded **once at startup** into an in-memory store behind a repository
interface, so the pricing source can change later without touching the engine.

```text
billing/
  BillingConfigRepository           (interface)
      Optional<OrgBillingConfig> findByOrgId(String orgId)
      List<OrgBillingConfig>     findAll()
  InMemoryBillingConfigRepository   (holds Map<String,OrgBillingConfig>)
  BillingDataSeeder                 (ApplicationRunner / @PostConstruct)
      → reads classpath:billing/org-billing-config.json
      → deserialises to List<OrgBillingConfig> (Jackson)
      → assigns tierNumber by list order, validates, populates the repository
```

- **Source of truth:** `src/main/resources/billing/org-billing-config.json` — the exact
  8-org JSON supplied (field names map directly to `OrgBillingConfig`). Editing pricing
  = editing this file; no recompile of logic.
- **Validation at load:** non-empty tiers; exactly one unbounded (`null`) tier and it
  must be **last**; rates ≥ 0; `orgId` unique. Fail fast on a bad config.
- **Swap-in path:** because the engine depends only on `BillingConfigRepository`, a
  future `JdbcBillingConfigRepository` backed by the `feereport` table (§2.3) can replace
  the in-memory + JSON loader with **zero change** to `BillingEngineService`.

### 8.5.4 The eight supported organisations

| Organisation | orgId | Tiered | Free tier | Min-fee floor | Fixed monthly | Waive |
|---|---|:--:|:--:|:--:|:--:|:--:|
| Prudential Life Assurance | 0107537001897 | ✓ | – | – | – | ✓ |
| Tokio Marine Life Insurance | 0107540000103 | ✓ | ✓ (50k@0) | – | – | ✓ |
| Muang Thai Insurance | 0107551000151 | ✓ | – | ✓ 42,000.00 | – | – |
| Dhipaya Life Assurance | 0107556000051 | ✓ | ✓ (240k@0) | – | – | ✓ |
| BCI (Thailand) | 0125562016973 | ✓ | ✓ (30k@0) | – | – | ✓ |
| Government Savings Bank | 0994000164891 | ✓ | – | ✓ 0.00* | – | – |
| Krungthai Bank | 0107537000882 | ✓ | – | – | ✓ 30,000.00 | – |
| Advanced Digital Distribution | 0105561025634 | ✓ | – | ✓ 28,000.00 | – | – |

\* GSB's `minimumFee = 0.00` is an explicit **zero floor** (a no-op guarantee); kept
for config symmetry. Every org uses Progressive Tiered as its base.

### 8.5.5 Assumptions & open questions — **[CONFIRM]**

1. **`maxCapacity` = tier width, not cumulative ceiling.** Confirmed by Prudential
   (`720000 ×4` repeated) — repeated values only make sense as widths. The engine
   treats them as widths.
2. **Minimum-limit threshold for the waive rule = first tier's capacity.** The supplied
   JSON has `waiveIfBelowMinimum` (boolean) but **no explicit threshold**. We derive it
   from `tiers[0].maxCapacity` (e.g. Prudential 720,000; Dhipaya 240,000). **Confirm**
   this is correct, or add an explicit `minimumCommitment` field to the config.
3. **Waive compares against *which* usage — monthly or accumulated YTD?** §8.3's original
   rule compared **accumulated** YTD usage to the minimum. The JSON is silent. Default
   assumption: compare the **report period's usage**; **confirm** vs. accumulated.
4. **Fixed Monthly Fee is additive** (`tiered + fixed`). Krungthai has both a fixed fee
   and tiers, so additive is the natural reading — **confirm** it isn't an alternative
   (either/or) model.
5. **orgIds vs. live data.** 6 of 8 orgIds exist in the imported `fmsv_db`
   (Tokio/Dhipaya/BCI/GSB/Krungthai/Advanced); **Prudential `0107537001897`** and
   **Muang Thai `0107551000151`** are **not** in the current dataset — their reports
   will be empty until data exists. Note: `0107537001897` (Prudential) is one digit off
   the real Krungthai `0107537000882` — **confirm** it's not a typo.

---

## 9. PDF rendering layer

### 9.1 `HtmlReportRenderer`
```text
String renderDaily(DailyReportData data)
String renderMonthly(MonthlyReportData data)
```
- Uses the Thymeleaf `TemplateEngine` (or `SpringTemplateEngine`) to process
  `report/daily_report.html` / `report/monthly_report.html` with the data model into a
  **fully-resolved HTML string** (no servlet context needed — process programmatically).
- HTML must be XHTML-clean (OpenHTMLToPDF requires well-formed XML): self-close tags, quote
  attributes, declare `<!DOCTYPE html>`.

### 9.2 `PdfRenderService`
```text
byte[] htmlToPdf(String html, String baseUri)
```
- Mirrors `ThaiPdfExample`:
  1. `PdfRendererBuilder` → `useFastMode()`, ICU bidi splitter + reorderer,
     `withHtmlContent(html, baseUri)`, `toStream(byteArrayOutputStream)`.
  2. `ShapedPdfBoxRenderer.build(builder)` (try-with-resources).
  3. `addShapingFont(noto, "Noto Sans Thai", true)` + optional Sarabun/CordiaNew.
  4. `renderer.layout(); renderer.createPDF();`
  5. Return the `ByteArrayOutputStream.toByteArray()`.
- **Font families used in the HTML/CSS `font-family` must exactly match the names passed to
  `addShapingFont`** (e.g. `'Noto Sans Thai'`), or shaping won't apply.
- Initialize fonts **once** (the `File` objects, extracted from classpath) and reuse; the
  builder/renderer are per-request and must be closed.
- **[VERIFY]** thread-safety of reusing font `File`s across concurrent renders (the `File`
  is just a path, so safe; the renderer is per-request).

### 9.3 HTML/CSS template design notes
- Shared `report.css`: `@page { size: A4; margin: …; }`, base `font-family: 'Noto Sans Thai'`
  (or CordiaNew for monthly), table styling, the TDID letterhead/footer block.
- **`daily_report.html`** — header (logo, title "Transaction Summary Report", subtitle from
  org name, "As of <Month>, <Year>", "Organization: <orgId>"); a two-column table
  (`Date | No. of Transaction`) iterating `data.rows`; a footer total row.
- **`monthly_report.html`** — TDID letterhead; title "A Transaction Summary Report of Signing
  Service for <org.displayName>"; "As of <Month> <Year>"; tier table
  (`Tier | Rate | No. of usage Transaction | Fee(Baht)`) iterating `data.lines`; totals;
  accumulated-transaction line; conditional remark block when `belowMinimum`; Run Date / Run
  Time; footer. Use the decoded template (§2.5) for exact wording.
- Logo: embed as a `static/` resource referenced via `baseUri`, or inline base64.

---

## 10. Web layer

### 10.1 `ReportController`
| Method & path | Purpose | Returns |
|---|---|---|
| `GET /` | Render the input form (`index.html`) | `text/html` |
| `GET /report` (or `POST`) — params `orgId`, `type`, optional `period` (`yyyy-MM`) | Generate & stream the PDF | `application/pdf` (attachment) |

- Response headers for download:
  `Content-Type: application/pdf`,
  `Content-Disposition: attachment; filename="<type>_<orgId>_<period>.pdf"`,
  `Content-Length`.
- Validate `orgId` (known in registry) and `type` (enum); on failure return a friendly error
  page / 400.
- Keep the controller thin: delegate to `ReportService.generate(...)`.

### 10.2 `index.html` (UI)
- A single form: text input **Organization ID**, radio/select **Report Type** (Daily /
  Monthly), optional **month** picker, **Download** submit button.
- Optionally populate a dropdown of known orgs from the registry for usability.
- Plain server-rendered Thymeleaf; no JS framework required.

### 10.3 `GlobalExceptionHandler`
- `@ControllerAdvice` mapping `UnknownOrgException` → 400/404 with a message; generic
  `Exception` → 500 with a sanitized message (never leak SQL/stack to the browser).

---

## 11. End-to-end request flow

```
Browser (GET /)            -> index.html form
User submits orgId+type    -> GET /report?orgId=…&type=DAILY|MONTHLY[&period=yyyy-MM]
ReportController            -> ReportService.generate(orgId, type, period)
  ReportService            -> OrgRegistry.lookup(orgId)            (404 if unknown)
                           -> DailyReportService / MonthlyReportService
     *ReportService        -> TransactionRepository (parameterized COUNT(s))
     MonthlyReportService  -> FeeCalculator (tiered) + minimum-limit rule
  ReportService            -> HtmlReportRenderer (Thymeleaf -> HTML)
                           -> PdfRenderService (thai-pdf-glyphshaping -> byte[])
ReportController           -> 200 application/pdf (attachment)
```

---

## 12. Step-by-step implementation guide (build order)

1. **Bootstrap project.** `start.spring.io` → Maven, Java 21, Spring Boot 3.3.x, deps: Web,
   Thymeleaf, JDBC, MySQL Driver. Add the PDF deps from §3.2.
2. **Install the local jar** (§3.1) and confirm the build resolves `io.github.de8tech:…`
   plus the explicit OpenHTMLToPDF/PDFBox/ICU deps. Run a throwaway main mirroring
   `ThaiPdfExample` to prove a Thai PDF renders inside the Spring app classpath **before**
   building anything else.
3. **Fonts.** Drop `NotoSansThai-Regular.ttf` (required) into `resources/fonts/`; add the
   classpath→temp-`File` extraction helper. Optionally add CordiaNew / Sarabun.
4. **Config & registry.** Add `application.yml`, `OrgRegistryProperties`,
   `FontProperties`; seed the four orgs from the scripts. Externalize DB creds via env.
5. **Domain types** (§6) — records/enums, no logic.
6. **Repository** (§7) — `JdbcTemplate` parameterized `countSuccess` + `countSuccessByDay`;
   write a `@JdbcTest` (or Testcontainers MySQL) verifying counts on seed data.
7. **FeeCalculator** (§8.4) — pure function; unit-test against the verified sample
   (`89,741 → 62,818.70`, all in tier 1).
8. **DailyReportService** (§8.2) — counts + zero-fill + total; unit test month boundaries
   (28/29/30/31-day months; the day-`31` script bug must not recur).
9. **MonthlyReportService** (§8.3) — usage + accumulated + fee + minimum-limit remark.
10. **HtmlReportRenderer + templates** (§9.1, §9.3) — build `daily_report.html` first
    (simpler), eyeball against `template_daily_report.pdf`; then `monthly_report.html`
    against `template_monthly_report.pdf`. Match wording from the decoded text in §2.4/§2.5.
11. **PdfRenderService** (§9.2) — wrap the renderer; reuse fonts; return `byte[]`.
12. **ReportController + index.html** (§10) — wire form → service → PDF download.
13. **GlobalExceptionHandler** (§10.3).
14. **Hardening** (§13): read-only DB user, no secrets in source, input validation.
15. **End-to-end test:** request DAILY for GSB (`0994000164891`) and MONTHLY for a fee-bearing
    org; open both PDFs in Adobe/Foxit; confirm Thai shaping, searchability, and that the
    numbers match a manual SQL count.

---

## 13. Security & operational considerations

- **Credentials.** The scripts embed `root` + a plaintext password. The app must use a
  dedicated **read-only** MySQL account, credentials supplied via environment variables /
  secret manager, never committed. Set Hikari `read-only: true`.
- **SQL injection.** Scripts interpolate values into SQL strings. The repository must use
  **bind parameters** exclusively.
- **Input validation.** `orgId` must match the registry; reject arbitrary values. `period`
  must parse as `yyyy-MM` within a sane range.
- **Resource hygiene.** Always close `ShapedPdfBoxRenderer` (try-with-resources); stream PDFs
  as `byte[]` for small reports, or to a temp file/streaming response if a report can be large
  (daily ≤ 31 rows and monthly ≤ a page — `byte[]` is fine).
- **Timezone.** Pin `Asia/Bangkok` for all date math and the Run Date/Time stamp; the scripts
  rely on server-local time.
- **Concurrency.** Renderer/builder are per-request; font `File`s are shared read-only. Cap
  concurrency if PDF rendering proves CPU-heavy.
- **PII / report access.** These are financial billing reports — add authentication/authorization
  before any non-trivial deployment (out of scope here but flagged).

---

## 14. Open questions / verification checklist

- [ ] Recover the full `daily_report_count.sh` header (query, date range, any extra filters).
- [ ] Confirm `transactions` schema & column types in `fmsv_db`.
- [ ] Fix tier→rate mapping for tiers 2–5 from the rendered monthly template (tier-1 = 0.70 ✓).
- [ ] Confirm the per-org **fee schedule** and **minimum-transaction** thresholds (contractual).
- [ ] Define the **accumulated transaction** window used by the monthly report.
- [ ] Confirm the exact **minimum-limit → billed 0.00** rule and remark text.
- [ ] Decide which orgs receive a *monthly billing* report vs. only a *daily count* report
      (the daily template is GSB-specific; the monthly template is invoice-style billing).
- [ ] Confirm `thai-pdf-glyphshaping` public method signatures + any non-`File` font overload.
- [ ] Pin exact OpenHTMLToPDF / PDFBox / ICU4J versions matching the library jar.
- [ ] Resolve **CordiaNew** font licensing for bundling (else fall back to Noto/Sarabun).
- [ ] Source the logo image used in the templates.

---

## 15. Testing strategy (summary)

- **Unit:** `FeeCalculator` (tier math incl. the verified sample), `DailyReportService`
  zero-fill & month-boundary, date-range helpers.
- **Repository slice:** `@JdbcTest` / Testcontainers MySQL with seed rows; assert counts and
  the `gatewayId` filter (GSB).
- **Rendering:** golden-ish check — render the daily PDF, assert non-empty, A4, and that
  searchable text contains expected Thai/English tokens (`PDFTextStripper`).
- **Web slice:** `@WebMvcTest` for `ReportController` — content type, `Content-Disposition`,
  404 on unknown org.
- **Manual acceptance:** open generated PDFs in Adobe/Foxit; verify Thai shaping +
  Ctrl-F searchability per `PDF-Gen/TESTING.md`; reconcile totals against a hand-run SQL count.
```
