# Game Transaction Report

A small web application that reports on game transaction data held in MySQL 8,
over a date range, with per-column filtering, sorting and pagination.

Spring MVC + JSP on the front, JPA/Hibernate over native SQL on the back.

---

## Contents

- [Requirements](#requirements)
- [Running it — Docker](#running-it--docker)
- [Running it — local MySQL](#running-it--local-mysql)
- [Configuration](#configuration)
- [What the report does](#what-the-report-does)
- [Server design](#server-design)
- [Client design](#client-design)
- [Database](#database)
- [Tests](#tests)
- [Project layout](#project-layout)
- [Known limitations](#known-limitations)

---

## Requirements

| | Version |
|---|---|
| JDK | 17 |
| Maven | 3.8+ (or use the bundled `mvnw`) |
| MySQL | 8.x |
| Docker | optional — only for the containerised path |

Java 17 is required rather than 8: Spring Boot 3.x runs on Jakarta EE 9+ and
does not support Java 8.

---

## Running it — Docker

One command, nothing to install but Docker:

```bash
docker compose up --build
```

Then open <http://localhost:8080/report>.

The first run takes a few minutes — it pulls the base images, compiles the
application inside a container, and imports the ~7,000 rows of transaction data.

What it starts:

- **`gtr-mysql`** — MySQL 8 with the schema, data and indexes baked into the
  image (see `db/Dockerfile`). Published on host port **3307**, deliberately not
  3306, so it does not clash with a MySQL you may already be running locally.
- **`gtr-app`** — the application on port **8080**.

The app waits for MySQL to report healthy before it starts
(`depends_on: condition: service_healthy`). MySQL needs 20–30 seconds to
initialise and import the data; without that gate the app would start first,
fail to connect, and crash-loop in a way that looks like an application bug.

To stop, and to discard the database volume:

```bash
docker compose down        # stop
docker compose down -v     # stop and delete the data volume
```

---

## Running it — local MySQL

Use this if you would rather run against your own MySQL instance.

**1. Create the schema and load the data.** Run the three scripts in `db/`, in
order — the numbering is the order:

```bash
mysql -u root -p < db/00_database.sql
mysql -u root -p < db/01_account_tran.sql
mysql -u root -p < db/02_indexes.sql
```

- `00_database.sql` creates the `gamedb` database (the supplied script contains
  only `CREATE TABLE` and data, so the database has to exist first)
- `01_account_tran.sql` is the script supplied with the assignment, unchanged
  except for one required fix — see [Database](#database)
- `02_indexes.sql` adds the indexes this report needs

**2. Create the application's database user:**

```sql
CREATE USER IF NOT EXISTS 'gamedb'@'localhost' IDENTIFIED BY 'gamedb';
GRANT ALL PRIVILEGES ON gamedb.* TO 'gamedb'@'localhost';
FLUSH PRIVILEGES;
```

**3. Build and run:**

```bash
mvn clean package          # or ./mvnw clean package
java -jar target/transaction-report.war
```

Then open <http://localhost:8080/report>.

To verify the data loaded:

```sql
SELECT COUNT(*) FROM gamedb.account_tran;   -- 6977
```

---

## Configuration

Everything is in `src/main/resources/application.yml`. The database settings
accept environment variables, so nothing needs editing to point at a different
server:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/gamedb` |
| `DB_USERNAME` | `gamedb` |
| `DB_PASSWORD` | `gamedb` |

The `docker` profile (`application-docker.yml`) overrides the host to `mysql`,
which is the Compose service name — inside a container `localhost` refers to the
container itself, not to the database.

The `report:` block controls the report's behaviour: which table to read, which
columns count as money, which transaction types count as bets and wins, the
offered page sizes, and the CSV export limits. Sensible defaults are built in,
so the block can be removed entirely and the app still runs.

### A note on timezones

`account_tran.DATETIME` is a MySQL `TIMESTAMP`. **The application and the
database must agree on a timezone**, or every timestamp shown is offset and
date-range filtering silently includes or excludes rows near the boundaries.

The two supported paths each satisfy this differently:

- **Docker** — MySQL runs UTC (the container default) and the JVM is pinned to
  UTC via `JAVA_OPTS`. They agree by construction.
- **Local** — both the app and MySQL use the host machine's timezone, so they
  agree because they share a machine.

What must be avoided is asserting a timezone that is not true of the actual
server. An earlier version of the configuration carried
`serverTimezone=UTC` in the JDBC URL while the local MySQL was running with
`time_zone = SYSTEM`; the driver converted on false information and every
timestamp came back shifted by the local UTC offset. If timestamps in the report
do not match a direct `SELECT` against the table, this is the first thing to
check.

---

## What the report does

**Search form** — start date/time, end date/time (both required, start must be
less than or equal to end), and an optional account ID.

**Report table** — nine columns: `id`, `account_id`, `datetime`, `tran_type`,
`platform_tran_id`, `game_tran_id`, `game_id`, plus two derived values:

- `amount` — the sum of every `AMOUNT_*` column
- `balance` — the sum of every `BALANCE_*` column

**Filters** — `platform_tran_id`, `game_tran_id`, `game_id` (prefix match),
`account_id` and `tran_type` (exact match).

**Sorting** — click any column header; clicking the active column reverses it.

**Pagination** — 25, 50 or 100 rows per page, with the total count shown.

**Summary** — total staked, total paid out, and net (`win − bet`) across the
whole filtered result set, not just the visible page.

**CSV export** — the current filter and sort, ignoring pagination.

---

## Server design

### Request path

```
GET /report?…
   ↓
ReportCriteria          form-backing object; Spring binds and validates
   ↓
ReportController        checks validation, populates the model, picks the view
   ↓
ReportService           normalises paging/sorting, runs totals first, clamps
   ↓
ReportQueryBuilder      turns criteria into SQL + named bind parameters
   ↓
TransactionReportRepository   executes it as a JPA native query, maps rows
   ↓
MySQL                   filters, sorts and pages
   ↓
report.jsp              renders
```

The most important thing to know: **filtering, sorting and paging all happen in
the database.** The JVM never holds more than the 25–100 rows currently on
screen. Sorting by a derived column means sorting by a SQL expression, not
pulling rows into memory to compare them.

### Why the money columns are discovered, not hard-coded

The specification defines `amount` as *"the sum of all the `AMOUNT_*` columns"*.
That is a property of the schema, not of the code — so hard-coding a list means
the report silently goes wrong the day a column is added.

`TransactionColumnRegistry` queries `INFORMATION_SCHEMA` once at startup, keeps
the columns matching each prefix, and builds the SQL sum expressions from what is
actually there. On the supplied schema it resolves to six `AMOUNT_*` and four
`BALANCE_*` columns; the exact list is logged at startup and shown in the page
footer, so it is never a mystery what the two totals are made of.

An explicit list in `report.amount-columns` / `report.balance-columns` always
overrides discovery, which is useful for a schema that names its money columns
differently, and for tests that should not depend on a catalog lookup.

If neither configuration nor discovery yields any money column, startup fails
with a message naming the likely cause — usually that the SQL scripts have not
been run. Failing at startup is deliberate: a report with no money columns is not
worth serving, and a clear failure beats a page of empty cells.

### Why native SQL rather than JPQL or Criteria

The set of columns being summed is not known until runtime, so no static entity
mapping can express it — and sorting by those derived values has to happen in the
database. `ReportQueryBuilder` therefore assembles native SQL, which
`TransactionReportRepository` runs through `EntityManager.createNativeQuery`, so
it still executes inside the same Hibernate session and transaction as everything
else.

The JPA entity (`AccountTran`) still exists and is still used, through
`AccountTranRepository`, for the two queries it suits: the distinct
`TRAN_TYPE` values behind the filter's autocomplete, and the table's
minimum/maximum date used to prefill the search form.

### Why the generated SQL is safe

Two separate mechanisms, for two separate problems:

**Values** are always named bind parameters, never concatenated. A filter
containing SQL syntax is searched for as literal text.

**Identifiers** cannot be bind parameters — `ORDER BY ?` is not valid SQL, and
in MySQL it silently sorts by a constant rather than erroring. So the sort key
goes through a **fixed whitelist**: a map from the keys the URL may contain to
the SQL each one means. A key that is not in the map never reaches the query
text; it falls back to the default sort rather than throwing, so a stale
bookmark degrades gracefully. Column names discovered from the catalog are
additionally checked against a plain-identifier pattern before they can appear in
an expression.

`ReportQueryBuilder` deliberately has no Spring or JPA imports. It is a plain
object, so its output can be asserted directly in unit tests with no application
context and no database.

### Details worth knowing

**`ORDER BY` always carries a tie-breaker.** Sorting by a non-unique column such
as `tran_type` leaves rows with equal values in an undefined order, so the same
row can appear on two different pages while another never appears at all.
Appending `ID` makes the ordering total and paging stable.

**Count and totals run before the rows, in one query.** They share an identical
`WHERE` clause, so running them separately would scan the same range twice. It
also means an empty range costs one query instead of two, and knowing the total
first allows a page number past the end to be clamped to the last page rather
than rendering an empty grid.

**Every money column is wrapped in `COALESCE`.** `AMOUNT_REAL` and
`BALANCE_REAL` are nullable while the bonus columns are not; in SQL a single
`NULL` in a sum makes the whole sum `NULL`, which would render as a blank cell.

**Text filters are prefix matches, not "contains".** `LIKE 'abc%'` can use an
index; `LIKE '%abc%'` forces a scan of the whole date range. User input is
escaped so a typed `%` or `_` is treated as a character rather than a wildcard.

**Bet totals are reported as magnitudes.** In this dataset a wager is a debit —
`GAME_BET` rows carry a negative amount and `GAME_WIN` rows a positive one.
Summing raw values and subtracting would give a large positive number implying
players had won. Reporting the bet total as a magnitude makes `net = win − bet`
read correctly: negative when players lost more than they won.

Only `GAME_BET` and `GAME_WIN` feed the summary. The other eleven transaction
types in the data (deposits, cashouts, bonuses, rollbacks, tips) are counted and
listed but not classified as wagering. Which types count is configurable via
`report.bet-tran-types` / `report.win-tran-types`.

**Input is normalised before it reaches SQL.** `ReportService.normalize()`
clamps the page number to at least 1, replaces an unrecognised page size with
the default, and passes the sort key through the whitelist. Without it,
`?size=999999999` would attempt to load the entire table into memory and
`?page=-5` would produce a negative `OFFSET`.

---

## Client design

The whole UI is **one page driven by GET requests**. The search form, every
sortable column header and every pagination link submit the same criteria as
query parameters to the same endpoint. Nothing posts.

That single decision is why any view of the report is bookmarkable and
shareable, and why the browser's back button behaves.

Because each link is an independent request, every link must carry the current
filters forward. `ReportCriteria.toFilterQueryString()` emits just the filter
fields; `sort`, `dir` and `page` are appended by whichever link is being
rendered, since those are the parts each link overrides.

**Views** are JSP under `src/main/webapp/WEB-INF/jsp/`. `WEB-INF` is not
web-accessible, so a JSP can only be reached through a controller, never
requested directly by URL.

**`sortHeader.tag`** is a JSP tag file rendering one sortable column header —
the link, the current direction, the indicator arrow and the `aria-sort`
attribute. Nine headers, one implementation.

**Defaults are applied before binding.** On a first visit the date range is
prefilled from the table's own minimum and maximum, so the page opens showing
real data rather than an empty grid. This happens in an `@ModelAttribute` method
that runs *before* request parameters are bound — applying defaults afterwards
would not work, because the field would already have failed validation and
Spring renders a rejected field's original value rather than the bean's.

**Validation** is Bean Validation. `@NotNull` covers the individual dates;
`start <= end` needs a class-level constraint (`@ValidDateRange`) because a
field-level annotation can only see one field. When both dates are present but
out of order the error is attached to `endDateTime` so it renders beside the
field rather than as a page-level banner. When a date is missing, the range
check returns valid and lets `@NotNull` report it, so one mistake produces one
message.

**Styling** is a single hand-written stylesheet at
`src/main/resources/static/css/app.css`. No CSS framework, no build step, no
JavaScript.

---

## Database

The table is `account_tran`, created by the script supplied with the assignment.

**One fix was required.** The original DDL had a trailing comma after its last
`KEY` clause:

```sql
  KEY `IDX_ACCOUNT_TRAN_3` (`PLATFORM_TRAN_ID`,`PLATFORM_ID`),
) ENGINE=InnoDB ...
```

MySQL 8 rejects that with a syntax error. `db/01_account_tran.sql` is otherwise
the supplied file unchanged.

### Indexes

The supplied table already indexes:

| Index | Columns | Serves |
|---|---|---|
| `PRIMARY` | `ID` | sorting by id |
| `IDX_ACCOUNT_TRAN_1` | `ACCOUNT_ID, DATETIME` | account filter + range |
| `IDX_ACCOUNT_TRAN_2` | `GAME_TRAN_ID, PLATFORM_ID` | game_tran_id filter |
| `IDX_ACCOUNT_TRAN_3` | `PLATFORM_TRAN_ID, PLATFORM_ID` | platform_tran_id filter |

What it does not cover is the two commonest shapes of this report: a bare date
range, and a date range narrowed by `GAME_ID` or `TRAN_TYPE`. `db/02_indexes.sql`
adds three:

```sql
CREATE INDEX ix_account_tran_datetime          ON account_tran (`DATETIME`);
CREATE INDEX ix_account_tran_game_datetime     ON account_tran (GAME_ID, `DATETIME`);
CREATE INDEX ix_account_tran_trantype_datetime ON account_tran (TRAN_TYPE, `DATETIME`);
```

Every query the application issues is bounded by `DATETIME`, so each index pairs
a filter column with it. That lets InnoDB satisfy filter + range + `ORDER BY`
from a single index rather than a full scan followed by a filesort.

Sorting by `amount` or `balance` is the exception and cannot use an index — those
values are computed at query time and exist nowhere on disk, so MySQL must
evaluate the expression across the filtered range and sort the result. On a very
large table, a stored generated column with its own index would be the fix.

### One thing to be careful of

The entity maps `DATETIME` as `` @Column(name = "`DATETIME`") `` — with
backticks, because `DATETIME` is a reserved word. Spring Boot's default naming
strategy lower-cases `@Column` names and breaks the quoted identifier, so
`application.yml` pins `PhysicalNamingStrategyStandardImpl`, which takes the
annotation literally. The symptom of removing that line is confusing: the native
queries keep working while the JPQL ones fail.

---

## Tests

```bash
mvn test
```

66 tests across six classes.

**`TransactionReportRepositoryTest` (15)** — runs the generated SQL against H2 in
MySQL mode, so the statement is executed rather than only inspected. The fixture
mirrors the real data's conventions: negative wagers, loyalty points as
`BIGINT`, and one row with a null `AMOUNT_REAL` so the `COALESCE` is genuinely
exercised. Covers date-range filtering, each column filter, prefix matching, that
a user-typed `%` matches literally and returns nothing, paging without overlap,
descending sort, and the bet/win/net totals.

**`ReportServiceTest` (13)** — the service with a mocked repository. Page and
size clamping, the empty-range short-circuit (verified by asserting the row query
is never issued), the offset derived from the clamped page rather than the
requested one, the cached data window, and the tran-type lookup degrading to an
empty list rather than failing the page.

**`CsvExportServiceTest` (11)** — RFC 4180 quoting, chunked fetching, the row
cap, and the spreadsheet formula-injection guard together with its exception:
text beginning `=`, `+`, `-` or `@` is prefixed with an apostrophe, but a numeric
value never is, or a negative amount would stop being a number in the
spreadsheet.

**`ReportQueryBuilderTest` (22)** — asserts the generated SQL directly. The
builder is a plain object with no framework dependencies, so these need no
Spring context and no database; the whole class runs in under a fifth of a
second. Grouped into:

- *Sort whitelist* — every documented key is accepted, an unknown key falls back
  to the default rather than throwing, and a key of
  `"id; DROP TABLE account_tran; --"` produces SQL containing neither `DROP` nor
  `--`. That last one is the injection defence, demonstrated rather than
  asserted.
- *Order by* — direction follows the criteria, `ID` is appended as a tie-breaker
  on a non-unique sort so paging is stable, and no redundant tie-breaker is
  added when already sorting by `ID`.
- *Where clause* — the date range is always bound; unset filters add no
  predicate at all; each supplied filter adds exactly one predicate and one bind
  parameter; and a value containing SQL syntax ends up in the parameter map, not
  in the statement.
- *Like escaping* — a user-typed `%` becomes a literal `\%`, leaving the
  trailing wildcard as the only one that matches.
- *Summary* — one `FROM`, so the range is scanned once rather than twice; the
  same filters as the row query; `COALESCE` so an empty range totals zero rather
  than null; and an empty tran-type list producing `1 = 0` rather than the
  syntax error `IN ()`.

**`ReportCriteriaValidationTest` (4)** — the date-range constraint: a valid range
passes, start after end is rejected with the error attached to `endDateTime`,
equal dates are allowed, and a missing date reports only `@NotNull` rather than
two errors for one mistake. Builds a `Validator` directly, so no Spring context.

**`TransactionReportApplicationTests` (1)** — the standard context-load smoke
test.

**`mvn test` currently requires a running MySQL**, because the context-load test
starts the full application and therefore needs a datasource. The other 65 tests
have no such dependency — the repository tests use H2 in memory. Either bring the
database up first, or skip tests with `mvn package -DskipTests`.

A note on the test profile: `application-test.yml` deliberately sets no
`hibernate.jdbc.time_zone`. The H2 fixture rows are inserted by a plain SQL
script that does no timezone conversion, so reading them back under a UTC
`jdbc.time_zone` on a JVM in another zone shifts every value by the local offset
and the exact-timestamp assertions fail everywhere except on a UTC machine.

The obvious next tests, roughly in order of value:

1. `ReportController` with `@WebMvcTest` — that validation errors render on the
   form rather than as a 400, and that the `@ModelAttribute` defaults appear on a
   first visit but not after a submit.
2. Replacing the context-load test's live datasource with H2, so `mvn test`
   needs no running database at all.
3. A pagination test over a larger fixture, asserting that every row appears
   exactly once across all pages when sorting by a non-unique column — the
   regression test for the `ORDER BY` tie-breaker.

---

## Project layout

```
transaction-report/
├── db/
│   ├── 00_database.sql          creates the gamedb database
│   ├── 01_account_tran.sql      supplied schema + data (one DDL fix)
│   ├── 02_indexes.sql           reporting indexes
│   └── Dockerfile               MySQL image with the scripts baked in
├── src/main/java/com/bet99/report/
│   ├── config/
│   │   ├── ReportProperties     typed binding of the report: config block
│   │   └── ReportConfig         constructs the query builder as a bean
│   ├── domain/AccountTran       JPA entity (partial mapping)
│   ├── dto/                     TransactionRow, ReportSummary, ReportPage
│   ├── repository/
│   │   ├── TransactionColumnRegistry   discovers the money columns
│   │   ├── ReportQueryBuilder          criteria → SQL (no Spring, no JPA)
│   │   ├── SqlQuery                    SQL + its bind parameters
│   │   ├── TransactionReportRepository executes the native queries
│   │   └── AccountTranRepository       Spring Data, for the two JPQL lookups
│   ├── service/                 ReportService, CsvExportService
│   └── web/                     ReportController, ReportCriteria, validation/
├── src/main/resources/
│   ├── application.yml          datasource, JPA, view resolver, report config
│   ├── application-docker.yml   docker profile: database host override
│   ├── messages.properties      readable binding-error messages
│   └── static/css/app.css
├── src/main/webapp/WEB-INF/
│   ├── jsp/report.jsp
│   └── tags/sortHeader.tag
├── Dockerfile                   application image (multi-stage)
└── docker-compose.yml           MySQL + app
```

Note there are **two Dockerfiles**: `db/Dockerfile` builds the MySQL image,
`Dockerfile` at the root builds the application. Compose refers to each by
directory.

The application image is multi-stage — the build stage carries Maven and a full
JDK, the runtime stage only a JRE and the war. The container runs as an
unprivileged user rather than root.

---

## Known limitations

- **`mvn test` needs a running database**, because the context-load test starts
  the full application context. The other 65 tests do not.
- **The web layer is untested.** The controller, the JSP and the tag file are
  covered only by manual checking; there is no `@WebMvcTest`.
- **`*_RAW_LOYALTY` columns are included in the money totals.** They hold
  loyalty points as `BIGINT`, not currency, and `BALANCE_RAW_LOYALTY` is large
  enough to dominate `BALANCE_REAL`. They are included because the specification
  says the sum of *all* `AMOUNT_*` / `BALANCE_*` columns; excluding them is one
  line — `report.exclude-columns: [AMOUNT_RAW_LOYALTY, BALANCE_RAW_LOYALTY]`.
- **No authentication.** The report is open to anyone who can reach the port.
- **CSV export is capped** at `report.csv-max-rows` (200,000). Rows are streamed
  in chunks rather than materialised, but the cap is a hard stop.
- **The app healthcheck only proves the port is accepting connections**, not that
  the application is well. Spring Boot Actuator's `/actuator/health` would report
  on the datasource too.
- **Sorting by `amount` or `balance` cannot use an index**, as described above.
