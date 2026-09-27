# Manual Acceptance

Run from the repository root after `docker compose up -d --build`:

```powershell
.\demo.ps1 greenfield
.\demo.ps1 brownfield
.\demo.ps1 ambiguous
.\demo.ps1 repair
.\demo.ps1 safe-stop
.\demo.ps1 failover
```

Accept only API responses and persisted rows. Greenfield and brownfield must show generated production/test paths and successful real validation. Ambiguous must pause before workspace mutation and continue on revision 2. Repair must show at least two attempts and a repair proposal. Safe-stop must end `SAFE_STOPPED`. Failover must retain the same workflow/revision when the base URL changes to port 8081.

Reject release readiness if the outcome lacks production paths, tests, a successful fixed-capability attempt, complete criterion rows, policy decisions, or exact current-revision approvals.
