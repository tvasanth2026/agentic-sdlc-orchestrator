# Architecture

## System boundary

The platform separates a durable control plane from an isolated execution plane. PostgreSQL stores workflows, revisions, plans, tasks, dependencies, attempts, artifacts, validations and specialist invocations. Repository tools operate only on approved roots and revision-specific copies. Later commits add proposal application, fixed build capabilities, recovery and distributed task claiming without changing these boundaries.

## Current flow

1. `POST /api/v1/workflows` persists workflow revision 1 and dispatches requirement interpretation.
2. Ambiguous work pauses in `AWAITING_CLARIFICATION`; accepted answers create a child revision.
3. `POST /api/v1/workflows/{id}/plan` creates an isolated repository copy and baseline manifest.
4. Repository analysis and dynamic planning are persisted with content hashes.
5. The specialist orchestrator invokes all provider-neutral roles through one bounded model gateway.
6. Each invocation persists a task, dependency, execution attempt, validated artifact and invocation evidence.
7. The workflow pauses in `AWAITING_CHANGE_APPROVAL` with the exact plan hash.
8. A hash-bound apply request invokes implementation and test proposal agents through the selected model provider.
9. The governed patch engine applies those exact structured operations in the isolated workspace and persists proposal-to-diff lineage.
10. Fixed Maven capabilities validate the changed workspace; classified failures either enter bounded repair or restore the immutable baseline.
11. Successful validation produces criterion-level traceability and a hashed engineering outcome.
12. An authenticated release approver must approve that exact outcome hash before the workflow becomes `RELEASE_READY`.

## Orchestration and governance

The validated engineering plan is materialized as durable `plan-*` tasks and dependencies. Change approval opens implementation tasks, successful patch application opens validation, successful validation opens risk review and documentation in parallel, and outcome generation synchronizes those paths at release readiness. Release approval is the final barrier. Task state changes occur only after their corresponding evidence has been persisted.

Change and release gates use separate configured credentials and bind approvals to the current workflow revision and exact evidence hash. Stale or invented hashes are rejected. Operator cancellation records a safe stop and cancels unfinished tasks. Policy decisions for repository boundaries, allowed patch operations, secret detection, change control, and prohibitions on automatic push/deployment are persisted before mutation.

## Executable scenarios

The deterministic provider generates a connected URL-shortener service and HTTP controller rather than disconnected sample files. The generated behavior includes automatic and custom case-sensitive aliases, duplicate and invalid-alias handling, expiry with HTTP 410, redirects, and UTC daily analytics. It also generates unit and HTTP tests that run in the changed repository through Maven `clean verify`.

Brownfield execution integrates with the supplied Spring project. Greenfield execution adds the application entry point to a build-only seed. Ambiguous work remains mutation-blocked until authenticated clarification creates a new revision. The repair scenario injects a controlled compilation defect, classifies the real failure, creates a hash-bound repair proposal from bounded source evidence, and re-enters the same patch validator before retrying.

## Model boundary

`SpecialistAgent` is the provider-neutral role contract. `ModelBackedSpecialistAgent` supplies role-specific instructions and current-revision context to `BoundedModelGateway`; both deterministic and OpenAI providers implement `ModelProvider` and return the same `ModelResponse` contract.

The gateway enforces:

- configured context and output character limits;
- a hard call timeout;
- redaction of bearer tokens, common credential assignments and private keys before provider invocation;
- strict deserialization with unknown-property rejection;
- semantic validation of required fields and collection bounds;
- a shared JSON Schema used by the OpenAI Responses API in strict mode.

The deterministic provider needs no key and is the default. The optional OpenAI provider uses the Responses API and requires `OPENAI_API_KEY`. Provider selection does not change persistence, validation or orchestration behavior.

## Specialist roles

The automatically invoked roles are requirement interpretation, ambiguity analysis, repository analysis, task planning, architecture, implementation, test generation, validation diagnosis, repair, documentation, security/risk review and release readiness.

These commit-4 artifacts are advisory, current-revision outputs. Validation diagnosis and repair explicitly decline to invent failures when no real failure evidence exists. Release readiness remains false until production changes and tests have been applied and validated by later execution stages.

## Evidence model

Every specialist invocation records workflow, revision, task, agent role, provider, model, attempt number, input artifact hashes, complete output JSON, output hash, decisions, assumptions, risks, duration and generated artifact IDs. The corresponding engineering artifact records schema version, storage location, producer identity and validation status. A task completes only after provider execution, artifact persistence and structured-output validation succeed in the same transaction.

## Trust boundaries

- Model output is untrusted data and cannot directly execute a command or mutate source.
- Repository paths are resolved beneath configured approved roots.
- OpenAI credentials come only from environment configuration and are never inserted into model context.
- Source mutation requires an authenticated change approval for the exact current plan hash and runs only through the controlled proposal pipeline.
- Real compiler/test execution is limited to fixed capabilities introduced in commit 6.
- The validation executor exposes only Maven Wrapper `clean verify` and `clean test`; no model-supplied command reaches `ProcessBuilder`. Secret-bearing environment variables are removed from child builds and output is bounded before persistence.
- Real failures are classified and persisted. Repair agents receive bounded failure output, relevant current files and hashes, and the prior proposal. Corrected operations re-enter the same patch validator and applier. Retry/backoff is bounded, and terminal recovery verifies restoration against the immutable baseline manifest.

## Patch boundary

Model output for implementation, test generation and repair uses the `file-operation-proposal` schema. Operations are limited to `CREATE`, `UPDATE` and `DELETE`, with complete replacement content for writes and expected content hashes for updates/deletes. The same validated proposal object is persisted and handed to the applier for deterministic and OpenAI providers.

Before mutation, the patch engine validates every operation, including normalized relative paths, symbolic-link components, permitted roots/extensions, duplicate paths, traceability fields, operation count, total bytes and optimistic hashes. Writes use same-directory temporary files and atomic replacement where supported. Any failure restores the baseline snapshot and verifies its manifest.

## Durable coordination

PostgreSQL is the coordination authority. A task claim contains one worker owner, lease expiry, heartbeat, and monotonically increasing fencing token. Claims and takeovers use row locks in a transaction. Completion requires the current unexpired worker/token pair and records a unique effect key before changing task state, so stale workers cannot commit and retries cannot duplicate an effect. Startup and scheduled recovery remove expired claims and return only abandoned `RUNNING` tasks to `READY`; clarification and approval waits remain paused.

The Compose topology runs two non-root orchestrators against the same PostgreSQL database and workspace volume, demonstrating shared durable state and secondary-instance continuation.

## Security and operations

Health probes are public. Local API and Prometheus access use Basic authentication with role checks. The `prod` profile replaces local users with an OIDC JWT resource server and maps the `roles` claim to operator and approver authorities. Credentials are environment/config-tree inputs, model credentials are removed from build subprocesses, and TLS can be configured directly or terminated at a trusted forwarding proxy.

Micrometer records workflow, validation, recovery, repair, model, and lease events. Audit-grade details remain in the durable ledgers; computed success, retry, rollback, repair, validation, model-failure, MTTR, and p95 latency indicators are defined as PromQL in `docs/OBSERVABILITY.md`.
