# Zagranie Typer

## Project identity

This repository is the "Zagranie Typer" project.

It is completely separate from the old "ZawódTyper V2" project.
Never modify, reference, migrate, or reuse code from ZawódTyper V2 unless explicitly instructed.

Stack:
- Java
- Maven
- PostgreSQL

## General safety rules

- Never write to the production/database settlement state unless explicitly instructed.
- Never run settlement APPLY automatically.
- Never run destructive SQL.
- Never commit or push automatically.
- Never modify data just because a parser appears correct.
- Ambiguous betting-market semantics must remain unresolved until explicitly approved by the user.
- Do not infer bookmaker nomenclature when it is uncertain.

## Football settlement workflow

Settlement work must always be done sequentially, one market family at a time.

For each market:

1. Audit the remaining pending records and market variants.
2. Choose exactly one market.
3. Inspect the existing repository before creating new code.
4. Check whether an existing parser/main/test already handles or partially handles the market.
5. Prefer extending an existing parser over creating overlapping functionality.
6. Prepare:
    - parser changes,
    - tests,
    - Main in DRY_RUN mode.
7. Run tests.
8. Run DRY_RUN.
9. Present the complete DRY_RUN output for review.
10. STOP.

Do not add or run APPLY until the user explicitly approves the DRY_RUN.

After approval:
11. Add APPLY support.
12. Run APPLY only when explicitly instructed.
13. After commit/apply, run another DRY_RUN.
14. Confirm that previously auto-settleable records disappeared.

## Settlement rules

- Database statuses are PENDING / W / L / V.
- SettlementDecision.UNSUPPORTED is internal only.
- Never write UNSUPPORTED to the database.
- Ambiguous records remain PENDING.
- Only deterministically settled records may be included in SettlementUpdate.
- MULTI_UNVERIFIED must not be automatically aggregated unless existing repository logic explicitly supports it.

## API Football fixture eligibility

Automatic football settlement normally uses completed fixtures only:

- FT
- AET
- PEN

Other statuses must remain skipped unless explicitly handled.

## Parser rules

- Parser must understand the complete declared market.
- Never settle only one fragment of a composite bet.
- Do not silently reinterpret unknown formats.
- Subject/team matching must be deterministic.
- If the subject cannot be resolved safely, leave the record unsupported/pending.
- Preserve existing safety guards unless a data audit explicitly justifies changing them.

## Betting semantics

Do not assume that:
- +X / -X always means handicap,
- +X / -X always means over/under,
- bookmaker-specific shorthand is universal.

Market semantics must be established from:
- explicit market wording,
- existing project conventions,
- audited source data,
- or explicit user approval.

## Code quality

- Before changing code, search the repository for related implementations.
- Do not duplicate existing parsers.
- Keep changes narrowly scoped to the currently approved market.
- Run `mvn test` after changes.
- Show relevant test results.
- Show `git diff` before considering implementation complete.
- Do not commit automatically.

## Working style

When asked to audit:
- inspect first,
- report findings,
- do not modify files unless explicitly requested.

When asked to implement:
- make the smallest safe change,
- add or update tests,
- run tests,
- report exactly what changed.

If any domain assumption is uncertain:
STOP and ask the user instead of guessing.

## Database credentials

Database credentials are provided through environment variables:

- DB_URL
- DB_USER
- DB_PASSWORD

Never print, echo, log, inspect, or expose DB_PASSWORD.
Do not modify database credentials.
Use the inherited environment when database access is required.

Read-only database access and DRY_RUN settlement queries are allowed.
Database writes and APPLY still require explicit user approval.