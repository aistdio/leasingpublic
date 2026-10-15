# Progress

Spec: `docs/superpowers/specs/2026-10-14-autolease-qa-design.md` (approved 2026-10-14).
Work phase by phase (token limits). A plan is written only for the next phase.

## Next
Phase 2 — user tests v1.0 (Jira LEAS-10..14). Claude: guide and review only (`qa-reviewer`); bugs go through `/fix-ticket`.
Release candidate: branch `release/1.0.0` = `develop` b90224e (local only, nothing pushed to origin). Stand :8081 runs it (`integration-testing · v1.0.0`, Flyway 7.0, agent traces emptied for the user's baseline).
Stories LEAS-5..9 in «Ready for testing». After user sign-off (LEAS-13): accept in Jira → merge `release/1.0.0` into `main` (create `main`), tag `v1.0.0`, merge back into `develop` → add acceptance + production stands (`/release`).
Env: JDK = `C:\Program Files\JetBrains\PyCharm2025.3\jbr` (no system Java); Ollama qwen3:8b + qwen3:4b on host; Docker 29.

## Done
- [x] Spec, priorities, agents (dev-fixer, qa-reviewer, course-librarian), skills (/fix-ticket, /release, /checkpoint), CLAUDE.md, course index
- [x] Setup committed (568aed6); v1.0 plan written (5 stories, 16 tasks)
- [x] Jira company-managed LEAS: epics LEAS-1..3, LEAS-4 setup (done), stories LEAS-5..9, QA tasks LEAS-10..14; components backend/frontend/agent; version 1.0.0 with release notes; workflow К выполнению → В работе → Ready for testing → Готово / Открыто повторно → В работе
- [x] LEAS-5 platform merged to develop (e40ac41); stand :8081 up
- [x] LEAS-6 client data merged (3ebb1ef): schema, DB users/roles, schedule calculator, seed (2136 payments)
- [x] LEAS-7 client cabinet merged (bb2aea0): finance query service, /api/me API, web UI + PWA
- [x] LEAS-8 leasing assistant merged (7cada2a): tools, router, Ollama/mock clients, traces, orchestrator, POST /api/agent/ask
- [x] LEAS-9 assistant UI merged (04e40c7): chat with answer cards, manager trace view
- [x] Final whole-branch review; fixes merged (b90224e): app_users cascade (V7_0), 415/405 handlers, principal toString. 136/136 tests
- [x] Release check on stand: API 3/3 answers with traces via qwen3:8b; UI at 1280 px and 375 px

## Phases
- [x] 1. Setup + v1.0 build (Must)
- [ ] 2. User tests v1.0: requirements review, test cases, baskets, SQL, runner, Postman, web/mobile, bug loop; then Claude adds acceptance + production stands and deploys v1.0 (Must)
- [ ] 3. v1.1: 3 tools, chat history, observability, WireMock stubs, feature flag (Should)
- [ ] 4. v1.2 + incident; regression caught; rubric, router tests, security, model A/B, report (Should)
- [ ] 5. Keycloak single sign-on migration, GitLab CI, RestAssured, Gatling, masking, Charles, Mailpit (Could)
- [ ] 6. Reveal hidden ledger: found X of N planted defects, review missed ones
- [ ] 7. Theory Q&A, interview prep (separate)

## Bugs
| Key | Component | Status |
|---|---|---|
