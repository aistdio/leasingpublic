---
name: release
description: Use when preparing, building or shipping an AutoLease release (v1.0, v1.1, v1.2), or moving a release between environments.
---
# Release

Scope per release: spec §3.

**Before build**
1. As product owner, write the release's Jira stories with acceptance criteria. 1–2 stories get a deliberate gap (ambiguity, missing edge case, contradiction); record gaps in the ledger, not in Jira.
2. As team lead, create the user's QA tasks in Jira (type Task, assigned to QA): test design, baskets + reference SQL, test execution, regression, test report — each with a short Definition of Done.
3. Plan this release's planted defects in the ledger `~/.claude/autolease-private/planted-bugs.md`. Each must differ from examples already shown to the user (see memory).

**Build:** each story on `feature/LEAS-N-short-desc`, merged to `develop`; planted defects go inside normal feature commits with neutral messages. Cut the release candidate as `release/X.Y.Z`. After acceptance: merge it into `main` (create `main` from it on the first release), tag `vX.Y.Z`, merge back into `develop`.

**Ship**
4. Bump version, tag `vX.Y.Z`, release notes in Jira: features, fixed bug keys, known issues. Never mention planted defects.
5. Set `APP_ENV=integration-testing`. v1.0: tell the user the whole build is ready. v1.1/v1.2: deliver story by story — after each story, move it to "Ready for testing" in Jira and tell the user; when all are delivered, announce the release candidate for regression.
6. User signs off → product owner accepts in Jira → `APP_ENV=acceptance` → `APP_ENV=production`.
7. v1.2 only: after production, file the incident ticket as the support team.
8. Run `/checkpoint`.
