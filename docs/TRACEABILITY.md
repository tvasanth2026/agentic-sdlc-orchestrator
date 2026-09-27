# Assignment Traceability

This matrix maps every official assignment requirement to implementation, tests, runtime evidence, and reviewer steps. A row is complete only when all four columns contain verified current-revision evidence.

| ID | Requirement | Implementation | Automated tests | Runtime evidence / reviewer step | Status |
|---|---|---|---|---|---|
| OBJ-1 | Transform one requirement into a reviewable engineering outcome | Evidence-derived, hash-bound outcome with criterion traceability and release gate | End-to-end planning, apply, validation, outcome and approval test | Inspect outcome hash, file paths, attempts and criterion rows | COMPLETE-C7 |
| REQ-1 | Interpret intent, ambiguity and normalize the problem | Durable async requirement agent, normalized criteria/assumptions/constraints/risks, validated ambiguity output and clarification revisions | Clear, structurally ambiguous, domain-ambiguous, invalid-output, auth and lineage tests | Submit requirement, poll workflow analysis, clarify with exact question answers | COMPLETE-C2 |
| REQ-2 | Decompose requirements into sequenced dependent tasks | Requirement/evidence-driven planner with validated roles, dependencies and gates | Graph validity and requirement-difference tests | Call planning API and inspect persisted plan/hash | COMPLETE-C3 |
| REQ-3 | Reason about brownfield modules, APIs and data flows | Bounded repository tools, isolated baseline and analyzer for modules, packages, APIs, layers, persistence, tests and build conventions | Repository fixture analysis and planning API tests | Inspect repository map, source manifest and analysis hash | COMPLETE-C3 |
| REQ-4A | Orchestrate the full SDLC using an explicit dependency graph | Plan graph materialized as durable tasks and advanced from analysis through release approval | Graph, persisted state and all-tasks-complete integration assertions | Inspect `plan-*` tasks, dependencies and evidence | COMPLETE-C7 |
| REQ-4B | Sequential and parallel paths with synchronization | Implementation/validation sequence, parallel risk and documentation paths, release barrier | End-to-end graph state assertions | Inspect dependency graph and final task states | COMPLETE-C7 |
| REQ-4C | Entry/exit gates and cross-stage context | Evidence gates, exact-hash approvals and persisted artifacts passed between every stage | Missing evidence, wrong token and stale hash tests | Inspect approvals, policies, artifacts and outcome | COMPLETE-C7 |
| REQ-4D | Preserve decision lineage and re-plan changed upstream work | Revision lineage, persisted dynamic graph, current-revision hashes and approval invalidation | Clarification lineage and stale-hash rejection tests | Revision, graph, audit and current hashes | COMPLETE-C7 |
| REQ-4E | Human approval for high-impact actions | Authenticated change/release/operator gates with exact evidence hashes | Wrong token, stale hash and successful gate tests | Approval and audit ledgers | COMPLETE-C7 |
| REQ-4F | Bounded retry, fallback, rollback and safe stop | Classified bounded repair, authenticated safe stop and verified baseline rollback | Repair, retry, cancellation and terminal rollback tests | Persisted attempts, decisions, audits and manifest evidence | COMPLETE-C7 |
| REQ-4G | Security, compliance and change-control policy guardrails | Repository, patch, secret, approval, no-push and no-deployment policies | Policy enforcement and approval authentication tests | Inspect policy decision and approval ledgers | COMPLETE-C7 |
| REQ-4H | Audit-grade observability and traceability | Invocation, attempts, artifacts, policies, approvals, audits, claims, outcomes and criterion lineage | Migration, orchestration and coordination assertions | Query ledgers through the current revision | COMPLETE-C8 |
| REQ-4I | Success, retry/rollback, MTTR and latency metrics | Micrometer counters/timers plus exact computed PromQL | Execution paths and registry assertions | `/actuator/prometheus` and `docs/OBSERVABILITY.md` | COMPLETE-C8 |
| REQ-5 | Production code, API/schema, unit/integration tests and documentation | Connected URL service/controller plus generated unit and HTTP tests; scenario documentation | Generated behavior tests execute under real Maven | Persisted files, diff and successful build logs | COMPLETE-C7 |
| REQ-6 | Risks, trade-offs, failure scenarios and safety validation | Risk review, policy ledger, real validation, classified repair and verified rollback | Success, repair, credential removal, safe-stop and rollback tests | Attempt output, repair lineage, audits and rollback records | COMPLETE-C7 |
| REQ-7 | Controlled autonomy with human oversight | Caller cannot complete tasks; approval contracts | API and completion-evidence tests C1 | Reject manual completion request | COMPLETE-C1 |
| REQ-8 | Final summary with rationale, artifacts, risks and operational context | Runtime engineering outcome generated from the complete persisted evidence chain | Evidence-derived outcome and stale approval tests | Outcome API and `docs/ENGINEERING-OUTCOME.md` | COMPLETE-C9 |
| DEL-1 | Runnable end-to-end prototype | Java 21/Spring Boot/PostgreSQL/Flyway, two containers and API demos | Context, eight migrations, health and end-to-end tests | Compose and scenario demos | COMPLETE-C8 |
| DEL-2 | Architecture overview and key decisions | Architecture documents control/execution planes, agents, trust, governance and evidence | Model, orchestration and coordination tests | Reviewer architecture walkthrough | COMPLETE-C8 |
| DEL-3 | Greenfield scenario | Agent-generated entry point, service, controller, unit and HTTP tests for build-only seed | Generated unit/HTTP tests executed by real Maven | Scenario workflow evidence and generated diff | COMPLETE-C7 |
| DEL-4 | Brownfield scenario | Integrated alias, expiry and UTC analytics HTTP enhancement | Generated service and HTTP behavioral tests | Scenario workflow evidence and generated diff | COMPLETE-C7 |
| DEL-5 | Ambiguous scenario | Clarification/revision pipeline, mutation guard and downstream generated feature | No-mutation, authentication, revision lineage, invalidation and end-to-end tests | Clarify revision 1, then plan/apply/validate revision 2 | COMPLETE-C7 |
| DEL-6 | Setup instructions | README, deployment guide and reviewer guide | Wrapper and packaged build verification | Follow reviewer guide | COMPLETE-C8 |
| DEL-7 | Testing approach and engineering trade-offs | Manual acceptance, outcome and production deployment documents | Full Maven verification and JaCoCo gate | Reviewer guide/manual acceptance | COMPLETE-C9 |
| QUAL-1 | Modular, testable, reliable, secure and scalable design | Bounded package contracts, durable evidence, isolated workspaces and role-separated governance | Full integration, security boundary, recovery and schema tests | Build, scenario, audit and traceability evidence | COMPLETE-C7 |
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
