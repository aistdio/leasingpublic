# Progress

Spec: `docs/superpowers/specs/2026-10-14-autolease-qa-design.md` (approved 2026-10-14).
Work phase by phase (token limits). A plan is written only for the next phase.

## Next
Phase 1 — commit setup files; write plan for v1.0 only (superpowers:writing-plans); execute via superpowers:subagent-driven-development: Opus orchestrates, implementer/reviewer subagents run on Sonnet (pass model). Jira must be connected before `/release v1.0` stories step.
User in parallel: register Jira Cloud Free, create project `LEAS`, connect Atlassian connector.

## Done
- [x] Spec, priorities, agents (dev-fixer, qa-reviewer, course-librarian), skills (/fix-ticket, /release, /checkpoint), CLAUDE.md, course index

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
