# Executable Scenarios

All scenarios use the same requirement-to-plan-to-proposal-to-validation pipeline. They never accept caller-supplied source or completion output.

## Greenfield

Use a build-only Spring Boot seed below an approved repository root. Submit the URL-shortener requirement, approve the exact plan hash, apply the model proposal, and validate. The implementation proposal creates an application entry point when no Java source tree exists, plus a service, HTTP controller, unit tests, and HTTP tests.

## Brownfield

Use an existing Spring Boot repository. Repository analysis maps its controllers, services, persistence, tests, and conventions. Generated URL-shortener files join the discovered source sets and expose `POST /urls`, `GET /{code}`, and `GET /urls/{code}/analytics`. Proof covers custom alias reservation, duplicate conflict, invalid aliases, case sensitivity, expiry, redirects, total analytics, and UTC daily analytics.

## Ambiguous

Submit `Please make links better for customers`. The workflow pauses in `AWAITING_CLARIFICATION` without creating a workspace. An authenticated clarification creates revision 2, invalidates requirement-dependent output, reuses unaffected repository evidence where valid, and requires a new plan and approval.

## Repair

Include `repair scenario` in an otherwise complete requirement. The deterministic implementation agent deliberately emits an invalid compiler token. Real `clean verify` output is persisted and supplied to the repair agent with current source, tests, prior proposal, and hashes. The repair agent returns a governed `UPDATE`, validation runs again, and both attempts remain durable.

## Safe Stop And Rollback

An authenticated operator can stop a non-terminal workflow with `POST /api/v1/workflows/{id}/cancel`. A non-retryable or exhausted validation failure restores the immutable baseline and records whether the resulting manifest matches exactly.
