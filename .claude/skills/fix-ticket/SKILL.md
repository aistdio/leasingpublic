---
name: fix-ticket
description: Use when the user gives a Jira key (LEAS-N) to fix, or asks to handle, process or answer a reported bug.
---
# Fix ticket

1. Dispatch agent `dev-fixer` with the key and the user's notes. Don't read the code in the main session.
2. Relay its reply to the user in ≤3 lines: role, decision, Jira status. Add nothing about other bugs.
3. Fix merged → update the bugs table in `docs/PROGRESS.md`.
