# Assignment Traceability

This living matrix maps every official assignment requirement to planned implementation, tests, runtime evidence, and reviewer steps. A row is complete only when all four columns contain verified current-revision evidence. Commit 1 establishes contracts and foundations; later rows intentionally remain `PLANNED`.

| ID | Requirement | Implementation | Automated tests | Runtime evidence / reviewer step | Status |
|---|---|---|---|---|---|
| OBJ-1 | Transform one requirement into a reviewable engineering outcome | Workflow/revision, task, artifact, validation and execution contracts | Domain and API boundary tests | Submit workflow and inspect revision identity/hash | PARTIAL-C1 |
| REQ-1 | Interpret intent, ambiguity and normalize the problem | Durable async requirement agent, normalized criteria/assumptions/constraints/risks, validated ambiguity output and clarification revisions | Clear, structurally ambiguous, domain-ambiguous, invalid-output, auth and lineage tests | Submit requirement, poll workflow analysis, clarify with exact question answers | COMPLETE-C2 |
| REQ-2 | Decompose requirements into sequenced dependent tasks | Requirement/evidence-driven planner with validated roles, dependencies and gates | Graph validity and requirement-difference tests | Call planning API and inspect persisted plan/hash | COMPLETE-C3 |
| REQ-3 | Reason about brownfield modules, APIs and data flows | Bounded repository tools, isolated baseline and analyzer for modules, packages, APIs, layers, persistence, tests and build conventions | Repository fixture analysis and planning API tests | Inspect repository map, source manifest and analysis hash | COMPLETE-C3 |
| REQ-4A | Orchestrate the full SDLC using an explicit dependency graph | Validated planning graph C3 and automatic provider-neutral specialist chain C4; full scheduler C7 | Graph tests and 12-role persisted invocation integration test; scheduling tests C7 | Planning response and persisted task/dependency/attempt/artifact rows | PARTIAL-C4 |
| REQ-4B | Sequential and parallel paths with synchronization | Task dependency contract; barriers in C7 | Parallel/barrier tests in C7 | Scenario execution timeline | PLANNED-C7 |
| REQ-4C | Entry/exit gates and cross-stage context | CompletionEvidence enforces exit gate; gate engine in C7 | Missing gate rejection in C1; gate tests C7 | Policy and gate decisions | PARTIAL-C1 |
| REQ-4D | Preserve decision lineage and re-plan changed upstream work | Revision/audit contracts; replanner in C7 | Approval invalidation/replan tests C7 | Revision lineage and invalidated hashes | PLANNED-C7 |
| REQ-4E | Human approval for high-impact actions | Approval contract/schema; authenticated gates C7 | Role/hash/revision tests C7 | Change/release approval records | PARTIAL-C1 |
| REQ-4F | Bounded retry, fallback, rollback and safe stop | Recovery contracts in C1; implementation C6/C7 | Recovery tests C6/C7 | Attempts, repair and rollback evidence | PARTIAL-C1 |
| REQ-4G | Security, compliance and change-control policy guardrails | Policy contract/schema; policy engine C7 | Policy rejection tests C7 | Policy decision ledger | PARTIAL-C1 |
| REQ-4H | Audit-grade observability and traceability | Specialist invocation ledger records role/provider/model/input hashes/output hash/decisions/risks/duration/artifacts; broader audit and metrics C9 | V4 migration and planning persistence assertions | Query current revision invocation and artifact rows | PARTIAL-C4 |
| REQ-4I | Success, retry/rollback, MTTR and latency metrics | Actuator/Prometheus foundation; meters C9 | Meter/PromQL tests C9 | Prometheus queries and dashboards | PARTIAL-C1 |
| REQ-5 | Production code, API/schema, unit/integration tests and documentation | Provider-generated production/test file operations and governed application C5; complete URL product C8/C10 | Exact proposal/application and compiled-source-set path tests C5; scenario behavior C8 | Persisted proposals, operations, manifests and unified diff; real build logs C6 | PARTIAL-C5 |
| REQ-6 | Risks, trade-offs, failure scenarios and safety validation | Validation/artifact/policy contracts; risk agents C4 | Validator and repair tests C6 | Risk and validation artifacts | PARTIAL-C1 |
| REQ-7 | Controlled autonomy with human oversight | Caller cannot complete tasks; approval contracts | API and completion-evidence tests C1 | Reject manual completion request | COMPLETE-C1 |
| REQ-8 | Final summary with rationale, artifacts, risks and limitations | Evidence model C1; outcome generator C10 | Evidence-derived outcome tests C10 | `docs/ENGINEERING-OUTCOME.md` | PLANNED-C10 |
| DEL-1 | Runnable end-to-end prototype | Java 21/Spring Boot/PostgreSQL/Flyway foundation | Context, migration and health tests | Start Compose/app and query health | PARTIAL-C1 |
| DEL-2 | Architecture overview and key decisions | `docs/ARCHITECTURE.md` documents boundaries, flow, agents, trust and evidence | Model boundary and orchestration integration tests | Reviewer architecture walkthrough | PARTIAL-C4 |
| DEL-3 | Greenfield scenario | Scenario and full generated vertical slice C8 | Generated unit/HTTP tests C8 | `demo.ps1 greenfield` C10 | PLANNED-C8 |
| DEL-4 | Brownfield scenario | Integrated enhancement C8 | Runtime-path behavior tests C8 | `demo.ps1 brownfield` C10 | PLANNED-C8 |
| DEL-5 | Ambiguous scenario | Clarification/revision pipeline and mutation guard C2; complete generated-feature scenario C8 | No-mutation, authentication, revision lineage, invalidation and reuse tests | Poll `AWAITING_CLARIFICATION`, submit clarification and inspect revision 2; `demo.ps1 ambiguous` C10 | PARTIAL-C2 |
| DEL-6 | Setup instructions | Commit-specific setup in README | Wrapper verification | Follow README on clean machine | PARTIAL-C1 |
| DEL-7 | Testing approach, limitations and trade-offs | Verification/coverage docs finalized C10 | Full reactor verification | Reviewer guide/manual acceptance | PLANNED-C10 |
| QUAL-1 | Modular, testable, reliable, secure and scalable design | Package boundaries, immutable contracts and migration baseline | Foundation tests | Build, health and schema evidence | PARTIAL-C1 |
| QUAL-2 | Safe change management and engineering judgment | Approved-root tools, isolated workspace, exact provider proposals, patch policies, optimistic locking, atomic writes, manifests/diffs and verified rollback | CREATE/UPDATE/DELETE, traversal, root/type/duplicate/size/stale-hash and mid-patch rollback tests | Apply by exact plan hash and inspect persisted proposal-to-operation lineage | COMPLETE-C5 |

## Commit 1 evidence checklist

- `mvnw.cmd clean verify` passes on Java 21.
- Flyway applies `V1__agentic_control_and_execution_plane.sql` under the test profile.
- Liveness and readiness report `UP`.
- Submission accepts only requirement and repository path.
- Caller-supplied `state` and `output` receive HTTP 400.
- No manual task-completion endpoint exists.
- Domain completion requires an executor attempt, artifacts, validation results, successful tools and a passed exit gate.

## Commit 2 evidence checklist

- Submission persists workflow and revision 1 before dispatching bounded asynchronous analysis.
- Validated agent output contains normalized problem, requirement-specific acceptance criteria, assumptions, constraints, risks, risk level and ambiguity reasons.
- Structurally and semantically different requirements produce different analysis and clarification decisions.
- Ambiguous revisions enter `AWAITING_CLARIFICATION` with `sourceMutationAllowed=false`.
- Missing answers and invalid operator credentials are rejected without creating a revision.
- Accepted clarification creates revision 2 with a parent revision, actor and answer lineage.
- Requirement-derived outputs are invalidated and regenerated; repository-derived evidence is reused with an explicit source ID.

## Commit 3 evidence checklist

- Approved-root repository tools reject absolute paths, traversal, symbolic-link escapes, unsupported files and configured bounds.
- Each workflow revision receives an isolated repository copy and content-addressed baseline snapshot; failed planning discards its revision workspace.
- Repository analysis identifies modules, packages, APIs, architecture layers, persistence, migrations, tests, build conventions and acceptance-criterion impact.
- Structurally different requirements produce different implementation and test tasks instead of a fixed template.
- Plan validation rejects duplicate tasks, unknown roles, missing/self dependencies, cycles, unreachable tasks and missing validation, risk, release-readiness or approval gates.
- Planning persists immutable analysis and plan JSON with hashes, then advances the workflow to `AWAITING_CHANGE_APPROVAL`.

## Commit 4 evidence checklist

- One provider-neutral `ModelProvider` contract supports deterministic and OpenAI Responses API implementations.
- All 12 specialist roles use the same bounded gateway and strict specialist-output schema.
- Context and output bounds, timeouts, malformed output and secret redaction are enforced by tests.
- The mocked OpenAI contract test verifies strict JSON Schema request construction and response parsing without a network call.
- Repository planning automatically invokes every specialist and persists tasks, dependencies, attempts, artifacts, validation results and invocation evidence.
- Persisted evidence includes provider/model, input hashes, complete output JSON/hash, decisions, assumptions, risks, duration and generated artifact IDs.
- Release readiness and repair outputs do not claim success when real build/failure evidence is absent.

## Commit 5 evidence checklist

- Apply API accepts only the exact current plan hash; caller-supplied operations are rejected.
- Implementation and test agents emit strict `CREATE`, `UPDATE` or `DELETE` operations through the configured provider.
- The exact proposal bytes and hashes are persisted before being represented as applied-operation evidence.
- Every operation includes path, type, content rules, expected hash, reason, requirement, criteria, task and input hashes.
- Patch policy rejects traversal, non-normalized or non-permitted paths, unsupported extensions, duplicate paths, excessive operations/bytes and stale content hashes.
- Writes use temporary files and atomic replacement where available; any partial failure restores and verifies the baseline manifest.
- Successful application persists source manifests and a unified diff and advances to `EXECUTING`; compilation and behavioral validation remain commit 6/8 work.
