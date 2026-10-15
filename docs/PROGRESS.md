# Progress

Spec: `docs/superpowers/specs/2026-10-14-autolease-qa-design.md` (approved 2026-10-14).
Work phase by phase (token limits). A plan is written only for the next phase.

## Next
Phase 1 — executing plan `docs/superpowers/plans/2026-10-15-autolease-v1.0.md` via superpowers:subagent-driven-development (Sonnet implementer/reviewer). Progress ledger: `.superpowers/sdd/2026-10-15-autolease-v1.0/progress.md` (git-ignored) — resume at first task without "complete" (now: Task 14, branch `feature/LEAS-8-leasing-assistant`, Tasks 10–13 done there, not merged yet).
Ollama running on host (qwen3:8b), stand :8081 up (checked 2026-10-15 13:45).
Jira: old team-managed project deleted by user (2026-10-15); user creates company-managed Scrum project key LEAS. Then recreate LEAS-1..4 (epics 1.0/1.1/1.2 + task «Настройка проекта Jira»), stories LEAS-5..9 and QA tasks LEAS-10..14 from `~/.claude/autolease-private/jira-v1.0-issues.md`; add components backend/frontend/agent, version 1.0.0, statuses Ready for testing / Reopened.
Env: JDK = `C:\Program Files\JetBrains\PyCharm2025.3\jbr` (no system Java); Ollama has qwen3:8b + qwen3:4b; Docker 29.

## Done
- [x] Spec, priorities, agents (dev-fixer, qa-reviewer, course-librarian), skills (/fix-ticket, /release, /checkpoint), CLAUDE.md, course index
- [x] Setup committed (568aed6); v1.0 plan written (5 stories, 16 tasks)
- [x] Jira: stories LEAS-5..9, QA tasks LEAS-10..14 (project deleted; recreate — see Next)
- [x] LEAS-5 platform merged to develop (e40ac41); stand :8081 up
- [x] LEAS-6 client data merged (3ebb1ef): schema, DB users/roles, schedule calculator, seed (2136 payments)
- [x] LEAS-7 client cabinet merged (bb2aea0): finance query service, /api/me API, web UI + PWA

## Phases
- [ ] 1. Setup + v1.0 build (Must)
- [ ] 2. User tests v1.0: requirements review, test cases, baskets, SQL, runner, Postman, web/mobile, bug loop; then Claude adds acceptance + production stands and deploys v1.0 (Must)
- [ ] 3. v1.1: 3 tools, chat history, observability, WireMock stubs, feature flag (Should)
- [ ] 4. v1.2 + incident; regression caught; rubric, router tests, security, model A/B, report (Should)
- [ ] 5. Keycloak single sign-on migration, GitLab CI, RestAssured, Gatling, masking, Charles, Mailpit (Could)
- [ ] 6. Reveal hidden ledger: found X of N planted defects, review missed ones
- [ ] 7. Theory Q&A, interview prep (separate)

## Bugs
| Key | Component | Status |
|---|---|---|
