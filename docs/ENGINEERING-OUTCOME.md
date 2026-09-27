# Engineering Outcome

The final outcome is generated at runtime by `POST /api/v1/workflows/{id}/outcome` from persisted workflow evidence.

The outcome contains workflow/revision identity, original and normalized requirements, acceptance criteria, task graph, architecture, generated production and test paths, applied diffs and manifests, validation attempts, coverage, repairs, rollbacks, policies, risks, assumptions, approvals, artifact hashes, and the evidence-derived release-ready decision. `criterion_traceability` maps each acceptance criterion to planning tasks, production paths, test paths, attempt IDs, hashes, and real Maven feature proof. Release approval is accepted only for the exact persisted outcome hash.

Supporting evidence includes specialist outputs for architecture, documentation, risk, and release readiness; provider/model invocation records; structured file proposals; applied operations and unified diffs; source manifests; fixed-capability logs; repair lineage; rollback verification; policies; approvals; audits; and durable coordination records.
