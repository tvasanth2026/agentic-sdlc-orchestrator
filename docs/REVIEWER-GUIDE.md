# Reviewer Guide

1. Run `./mvnw clean verify` and inspect the JaCoCo report in `target/site/jacoco`.
2. Run `docker compose config --quiet`, `docker compose build`, and `docker compose up -d`.
3. Confirm both readiness endpoints on ports 8080 and 8081.
4. Run each `demo.ps1` scenario. The script prints API-returned workflow states, hashes, generated paths, attempt counts, and release decisions.
5. Inspect PostgreSQL tables `agent_invocations`, `patch_proposals`, `applied_file_operations`, `execution_attempts`, `policy_decisions`, `approvals`, `criterion_traceability`, `engineering_outcomes`, `task_claims`, and `audit_events`.
6. For failover, stop `orchestrator-1` after apply; the demo continues through `orchestrator-2` against shared state.

The strongest proof is the persisted chain: requirement hash -> analysis -> dynamic plan hash -> model proposal hash -> applied operation hashes/diff -> real Maven attempt -> criterion traceability -> outcome hash -> exact release approval.
