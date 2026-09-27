# Assignment Traceability

This living matrix maps every official assignment requirement to planned implementation, tests, runtime evidence, and reviewer steps. A row is complete only when all four columns contain verified current-revision evidence. Commit 1 establishes contracts and foundations; later rows intentionally remain `PLANNED`.

| ID | Requirement | Implementation | Automated tests | Runtime evidence / reviewer step | Status |
|---|---|---|---|---|---|
| OBJ-1 | Transform one requirement into a reviewable engineering outcome | Workflow/revision, task, artifact, validation and execution contracts | Domain and API boundary tests | Submit workflow and inspect revision identity/hash | PARTIAL-C1 |
| REQ-1 | Interpret intent, ambiguity and normalize the problem | Requirement/revision contracts; agents in C2/C4 | Clear/ambiguous contracts in C2 | Greenfield and ambiguous demos in C8/C10 | PLANNED-C2 |
| REQ-2 | Decompose requirements into sequenced dependent tasks | Task/dependency contracts; dynamic planner in C3 | Graph and requirement-difference tests in C3 | Inspect generated task-plan artifact | PLANNED-C3 |
| REQ-3 | Reason about brownfield modules, APIs and data flows | Repository workspace contract; analyzer in C3 | Repository fixture analysis in C3 | Brownfield repository-analysis artifact | PLANNED-C3 |
| REQ-4A | Orchestrate the full SDLC using an explicit dependency graph | Workflow/task contracts; scheduler in C7 | Graph scheduling tests in C7 | Persisted graph/state history | PLANNED-C7 |
| REQ-4B | Sequential and parallel paths with synchronization | Task dependency contract; barriers in C7 | Parallel/barrier tests in C7 | Scenario execution timeline | PLANNED-C7 |
| REQ-4C | Entry/exit gates and cross-stage context | CompletionEvidence enforces exit gate; gate engine in C7 | Missing gate rejection in C1; gate tests C7 | Policy and gate decisions | PARTIAL-C1 |
| REQ-4D | Preserve decision lineage and re-plan changed upstream work | Revision/audit contracts; replanner in C7 | Approval invalidation/replan tests C7 | Revision lineage and invalidated hashes | PLANNED-C7 |
| REQ-4E | Human approval for high-impact actions | Approval contract/schema; authenticated gates C7 | Role/hash/revision tests C7 | Change/release approval records | PARTIAL-C1 |
| REQ-4F | Bounded retry, fallback, rollback and safe stop | Recovery contracts in C1; implementation C6/C7 | Recovery tests C6/C7 | Attempts, repair and rollback evidence | PARTIAL-C1 |
| REQ-4G | Security, compliance and change-control policy guardrails | Policy contract/schema; policy engine C7 | Policy rejection tests C7 | Policy decision ledger | PARTIAL-C1 |
| REQ-4H | Audit-grade observability and traceability | Audit/artifact schemas; metrics and durable ledger C9 | Persistence/metrics tests C9 | Audit events, hashes and PromQL | PARTIAL-C1 |
| REQ-4I | Success, retry/rollback, MTTR and latency metrics | Actuator/Prometheus foundation; meters C9 | Meter/PromQL tests C9 | Prometheus queries and dashboards | PARTIAL-C1 |
| REQ-5 | Production code, API/schema, unit/integration tests and documentation | Governed operation contracts C5; URL product C8/C10 | Generated source/test discovery C5/C8 | Generated diff and real build logs | PLANNED-C5 |
| REQ-6 | Risks, trade-offs, failure scenarios and safety validation | Validation/artifact/policy contracts; risk agents C4 | Validator and repair tests C6 | Risk and validation artifacts | PARTIAL-C1 |
| REQ-7 | Controlled autonomy with human oversight | Caller cannot complete tasks; approval contracts | API and completion-evidence tests C1 | Reject manual completion request | COMPLETE-C1 |
| REQ-8 | Final summary with rationale, artifacts, risks and limitations | Evidence model C1; outcome generator C10 | Evidence-derived outcome tests C10 | `docs/ENGINEERING-OUTCOME.md` | PLANNED-C10 |
| DEL-1 | Runnable end-to-end prototype | Java 21/Spring Boot/PostgreSQL/Flyway foundation | Context, migration and health tests | Start Compose/app and query health | PARTIAL-C1 |
| DEL-2 | Architecture overview and key decisions | Architecture document in C4/C10 | Documentation consistency tests C10 | Reviewer architecture walkthrough | PLANNED-C4 |
| DEL-3 | Greenfield scenario | Scenario and full generated vertical slice C8 | Generated unit/HTTP tests C8 | `demo.ps1 greenfield` C10 | PLANNED-C8 |
| DEL-4 | Brownfield scenario | Integrated enhancement C8 | Runtime-path behavior tests C8 | `demo.ps1 brownfield` C10 | PLANNED-C8 |
| DEL-5 | Ambiguous scenario | Clarification/revision pipeline C2/C8 | No-mutation and lineage tests | `demo.ps1 ambiguous` C10 | PLANNED-C2 |
| DEL-6 | Setup instructions | Commit-specific setup in README | Wrapper verification | Follow README on clean machine | PARTIAL-C1 |
| DEL-7 | Testing approach, limitations and trade-offs | Verification/coverage docs finalized C10 | Full reactor verification | Reviewer guide/manual acceptance | PLANNED-C10 |
| QUAL-1 | Modular, testable, reliable, secure and scalable design | Package boundaries, immutable contracts and migration baseline | Foundation tests | Build, health and schema evidence | PARTIAL-C1 |
| QUAL-2 | Safe change management and engineering judgment | Isolated workspace/recovery contracts; governed patching C5 | Policy, hash and rollback tests | Manifests, diffs and approvals | PARTIAL-C1 |

## Commit 1 evidence checklist

- `mvnw.cmd clean verify` passes on Java 21.
- Flyway applies `V1__agentic_control_and_execution_plane.sql` under the test profile.
- Liveness and readiness report `UP`.
- Submission accepts only requirement and repository path.
- Caller-supplied `state` and `output` receive HTTP 400.
- No manual task-completion endpoint exists.
- Domain completion requires an executor attempt, artifacts, validation results, successful tools and a passed exit gate.
