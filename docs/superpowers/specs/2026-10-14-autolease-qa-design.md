# AutoLease QA Training Product — Design

Date: 2026-10-14 · Status: draft for review

## 1. Goal
Turn the existing leasing backend into a realistic product on which the user works ~2 weeks as the **only QA engineer**, mirroring the target vacancy "Тестировщик ИИ-агента (QA Engineer, LLM-продукт)".

**Roles.** User = QA. Claude plays **product owner**, **backend developer**, **frontend developer**. Bugs are routed via Jira component (`backend`, `frontend`, `agent`); roles sometimes push back ("can't reproduce", "works as designed", partial fix).

**Success.** User has done each kind of work hands-on and has **before/after metrics measured across releases**: number accuracy, correct tool choice, valid JSON, invented numbers, p95, tokens, regression time, rubric agreement.

## 2. Product
**AutoLease** — auto leasing for ИП/ООО. Client cabinet (web + mobile PWA) with **«Лизинговый ассистент»**: an AI agent answering in Russian about the client's own contracts with numbers from their data, as strict JSON.

**Data.** Flyway migrations: clients get type/ИНН/legal name; DB users with roles CLIENT/MANAGER; payments get due/paid dates, components (выкупная часть, удорожание, НДС, пени) and status CANCELLED. Generator with **fixed seed** (~30 clients, ~60 contracts, ~2000 payments) → reference numbers reproducible by SQL.

**Agent** — own ReAct orchestrator in Java (readable, testable):
- **ToolRouter** — deterministic intent rules that pick which tools the model may use for a question. This is what the user's JUnit router tests cover; the model's final tool choice is measured by the routing basket.
- **LlmClient** — `ollama` (local ~8B tool-calling model + a smaller one for A/B) and `mock` (deterministic, for JUnit, CI, load tests). No paid API.
- **Tools** (client id always from auth, never from model arguments):

| Tool | Answers | Mirrors vacancy tool |
|---|---|---|
| `payments_summary` | paid/accrued for a period | обороты |
| `payment_breakdown` | выкупная часть / удорожание / НДС / пени | разбивка по категориям |
| `contracts_overview` | contracts, cars, remaining debt | контрагенты |
| `upcoming_obligations` | payment load by month ahead | кассовые разрывы |
| `schedule` | schedule, next payment | регулярные платежи |
| `early_buyout_calc` | early buyout amount | — |

- **Answer JSON** (schema-validated): `answer`, `figures[]` (label, value, unit, period), `tools_used[]`, `trace_id`, `warnings[]`. No financial advice.
- **Trace** — every step (tool, arguments, result, latency, tokens, error) in Postgres; `GET /api/agent/traces/{id}` + trace view.

**UI.** Plain HTML/JS in Spring `static/`: login → cabinet (contracts, schedule, payments) → chat with JSON cards → trace view (manager). Responsive + PWA, `data-testid` on controls.

**Platform.** Gradle (from Maven), Swagger, JSON logs with `requestId`. Docker Compose profiles to keep the PC light: **core** (app, Postgres, Kafka + Kafdrop; Ollama runs natively on Windows for direct GPU access, app reaches it at `host.docker.internal:11434` and sets `num_ctx` per request), **observability** (Elasticsearch + Kibana + Filebeat, Prometheus + Grafana with an agent dashboard), **ci** (GitLab CE + Runner: build → router tests → API autotests with `mock` → Allure report).

## 3. Releases
| Release | Content |
|---|---|
| **v1.0** | Data + generator, cabinet UI/PWA, agent with `payments_summary`, `schedule`, `contracts_overview`. **Deliberately weak baseline** (routing, JSON, invented numbers) so metrics improve as the user's bugs get fixed |
| **v1.1** | 3 more tools, prompt change, chat history, observability profile; WireMock stubs for external systems (ИНН check, payment gateway: errors, timeouts); assistant behind a feature flag (enabled for part of clients) |
| **v1.2** | Fixes + prompt/model update that causes a regression (caught by run comparison); "production incident" ticket; GitLab CI |

**Delivery:** v1.0 is delivered as one build. v1.1 and v1.2 are delivered **story by story** ("LEAS-N is ready, test it") to the integration and functional testing environment; the user tests each story as it lands, then runs regression on the full release candidate.

Every release passes three environments (`APP_ENV`, shown in UI, logs, Grafana): **integration and functional testing environment** (user tests) → **acceptance testing environment** (product owner accepts on user's sign-off) → **production environment** (incident happens here).

**Stands (real copies, not labels):** each environment is its own app container with its own database in the shared Postgres; Kafka and Ollama shared. Integration and functional testing `localhost:8081` (`develop`, then `release/X.Y.Z`; edge-case test data), acceptance `localhost:8082` (`release/X.Y.Z`; masked copy of production data — the user's masking script), production `localhost:8080` (`main`, previous version; realistic client data). Deploy = run a versioned image (`autolease:X.Y.Z`) on a stand. Logs carry an `env` field (Kibana filter), Grafana has an env selector. A small background script simulates production clients (via `mock` LLM) so production has live logs/metrics for the v1.2 incident. v1.0 build has only the test stand; acceptance + production stands are added at the end of phase 2 when v1.0 is accepted.

**Planted defects (~18)**, hidden; categories only: calculations, period boundaries, routing, JSON, invented numbers, advice, redundant tool calls (latency visible in traces), prompt injection / other client's data, API, UI/mobile, log-only, metrics-only, race condition, memory leak. Concrete defects differ from all examples already shown to the user. List kept outside the repo.

## 4. QA track (user does, Claude guides)
Artifacts in `qa/`; tracker Jira Cloud Free, project `LEAS`.
1. **Requirements and test design** — review stories with deliberate gaps; test strategy; checklists and test cases (equivalence classes, boundary values, decision tables, state transitions) in Jira.
2. **Agent quality** — baskets (`qa/baskets/*.yaml`: accuracy, routing, JSON, safety) with measurable expectations; reference SQL; pytest runner + run comparison; assessor rubric calibrated against Claude; router JUnit tests; model A/B.
3. **API and data** — Postman, curl, JSON schemas, roles/other client's data, Kafka events; RestAssured + Allure in GitLab CI with endpoint coverage %; edge-case data + masking script (152-ФЗ).
4. **Web and mobile** — DevTools, real phone, Android emulator, Charles Proxy.
5. **SQL** (PostgreSQL, DBeaver) — JOIN across clients/contracts/payments/cars; GROUP BY + HAVING; inclusive date ranges; CTE (`WITH`); window functions (running totals, ranking); integrity checks (orphans via `LEFT JOIN … IS NULL`, duplicates via `HAVING COUNT(*) > 1`); queries over trace tables.
6. **Git** — user's own branches `test/LEAS-N-short-desc` for `qa/` artifacts, commits starting with the Jira key, merge into `develop` (merge request in GitLab once the `ci` profile is up), `git log` / `git blame` to localize a bug after it is found.
7. **Localization** — Kibana (requestId), Grafana, reading Java code.
8. **Load** — Gatling: parallel-request race scenario + long soak run (heap, heap dump).
9. **Bug loop** — Jira bug → key to Claude → fix on `bugfix/LEAS-N-short-desc` → retest → Closed / Reopened.
10. **Reports** — release sign-offs, final test summary with metrics.

**Branch convention** (GitFlow-style, Jira key uppercase): `feature/LEAS-N-short-desc` (stories), `bugfix/LEAS-N-short-desc` (bugs), `hotfix/LEAS-N-short-desc` (production incident, from `main`), `release/X.Y.Z` (release candidate), `test/LEAS-N-short-desc` (user's QA work). Long-lived: `develop` (integration) and `main` (= what is in production). After acceptance, `release/X.Y.Z` merges into `main` (tag `vX.Y.Z`) and back into `develop`. Environments: integration and functional testing runs `develop` builds while stories are tested one by one, then the `release/X.Y.Z` build for regression; acceptance runs `release/X.Y.Z`; production runs `main`.

## 5. Schedule (~2 weeks)
| Days | Claude | User |
|---|---|---|
| 1 | Stories in Jira, `CLAUDE.md`, `docs/PROGRESS.md`, SessionStart hook | Jira + Atlassian connector; course modules on theory and test design |
| 2–3 | Build v1.0 | Requirements review, test cases, baskets, reference SQL |
| 4–5 | Fix loop | Test v1.0 (API, web/mobile, baskets), runner |
| 6–7 | v1.1 | Regression, rubric, router tests, Kibana/Grafana |
| 8–10 | v1.2, incident, CI | Catch regression, Gatling, model A/B, API autotests, masking |
| 11–14 | Support | Final report; theory Q&A and interview prep (separate deliverables) |

**Priorities** (HR call in ~2 weeks, maybe earlier; technical interview later):
- **Must, by ~day 7:** v1.0; requirements review; test cases; Jira bug loop; baskets + reference SQL + pytest runner with first metrics; Postman; web/mobile.
- **Should, by ~day 12:** v1.1–v1.2 with caught regression; rubric; router tests; Kibana/Grafana; security attacks; model A/B; WireMock stubs; feature flag; final report.
- **Could** (after HR call, before technical interview): Keycloak single sign-on as a "company moves to single sign-on" migration story (first), GitLab CI, RestAssured volume, Gatling (race + soak), masking, Charles, Mailpit email capture (last). Cut from the end.

**Token budget:** user has 5-hour and weekly Claude limits → work strictly phase by phase; a plan is written only for the next phase.

## 6. Continuity
`CLAUDE.md` (rules, roles, Jira loop) + `docs/PROGRESS.md` (updated in every task commit) + SessionStart hook printing it + auto-memory.

## 7. Out of scope
RAG, Langfuse, DeepEval/RAGAS/promptfoo, paid LLM APIs, native mobile app, heavy UI automation, new Kafka features, file upload, GitHub Actions, real deployment.

## 8. Risks
- Local model weak in Russian tool calling → test 2–3 models first; `mock` covers CI and load.
- Elastic images blocked → OpenSearch Dashboards. Jira Cloud unavailable → Yandex Tracker (manual loop).
- Short on time → cut in order: Charles, model A/B, autotest volume, Gatling depth.
