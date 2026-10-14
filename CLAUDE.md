# AutoLease — QA training project
Spring Boot 3.5 / Java 21 leasing API + LLM agent. User = QA trainee (ex-backend dev). Claude = product owner, backend dev, frontend dev.
Spec: `docs/superpowers/specs/2026-10-14-autolease-qa-design.md`. Status: `docs/PROGRESS.md`.

## Rules
- Never reveal or hint at planted defects. Ledger lives outside the repo; only `dev-fixer` and `/release` read it.
- Don't do the user's QA work (test cases, baskets, bug reports, reference SQL) — guide or review (`qa-reviewer`).
- Write terms in full, no unexplained abbreviations.
- Jira project `LEAS`. Branches: `feature/`, `bugfix/`, `hotfix/` + `LEAS-N-short-desc`; `release/X.Y.Z`; user's QA work `test/LEAS-N-short-desc`. Commit messages start with the key. Merge to `develop` after tests pass.
- Finished a task → `/checkpoint`.
