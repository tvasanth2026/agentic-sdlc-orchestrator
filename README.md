# Agentic SDLC Orchestrator

This project implements a governed, repository-aware software engineering workflow for building and validating a URL shortener and similar Spring-based applications. It accepts a requirement, analyzes the target repository, creates a validated plan, invokes specialist roles, applies model-generated patch proposals through a controlled patch engine, runs real Maven verification in an isolated workspace, and requires exact-hash approvals before releasing an outcome.

## What this repository does

- Accepts a natural-language requirement and a repository path
- Interprets ambiguity and supports clarification-driven revisions
- Copies the target repository into an isolated workspace and records a baseline manifest
- Analyzes the repository structure and dependency footprint
- Builds and persists a dynamic engineering plan with task dependencies
- Invokes provider-neutral specialist agents through a deterministic or OpenAI-backed model gateway
- Produces file-operation proposals instead of direct repository mutation
- Applies only validated, hash-traced patches in an isolated workspace
- Executes real Maven `clean verify` in the modified workspace
- Classifies failures, supports bounded repair, and restores the baseline when needed
- Requires exact change and release approval hashes before final completion
- Keeps workflow, validation, approval, and artifact evidence durable in PostgreSQL

## Repository layout

- `src/main/java` — application logic for the orchestrator
- `src/test/java` — Spring tests for validation and workflow behavior
- `scenario-repositories/` — approved repository roots used during planning and generation
- `docs/` — architecture, scenarios, reviewer, observability, and deployment guidance
- `compose.yaml` — PostgreSQL + two orchestrator instances
- `demo.ps1` — script to run the built-in URL shortener scenarios

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop with Docker Compose
- PowerShell 7 or Windows PowerShell 5.1

## Local startup

Start PostgreSQL and run the app locally:

```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

The application listens on port `8080` by default. The default local credentials are:

- Username: `operator`
- Password: `local-development-only`

These can be overridden with:

```powershell
$env:AGENTIC_BASIC_USERNAME = "operator"
$env:AGENTIC_BASIC_PASSWORD = "local-development-only"
```

## Configuration

The app reads database and project settings from environment variables or defaults in `src/main/resources/application.yaml`.

Common settings:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/agentic_sdlc_orchestrator"
$env:DB_USERNAME = "agentic"
$env:DB_PASSWORD = "local-agentic-password"
$env:AGENTIC_REPOSITORY_ROOT = "./scenario-repositories"
$env:AGENTIC_WORKSPACE_ROOT = "./agent-workspaces"
$env:AGENTIC_CLARIFICATION_TOKEN = "local-operator-token"
$env:AGENTIC_OPERATOR_TOKEN = "local-operator-token"
$env:AGENTIC_CHANGE_APPROVER_TOKEN = "local-change-approver-token"
$env:AGENTIC_RELEASE_APPROVER_TOKEN = "local-release-approver-token"
```

Model configuration:

```powershell
$env:AGENTIC_MODEL_PROVIDER = "deterministic"   # or "openai"
$env:AGENTIC_MODEL_NAME = "deterministic-v1"
$env:OPENAI_API_KEY = "<your-key>"
$env:OPENAI_BASE_URL = "https://api.openai.com/v1/responses"
```

The default `deterministic` provider requires no API key. When using OpenAI, the model provider still follows the same contract and output validation rules.

## Verify health and build

Check the application is healthy:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/liveness
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
```

Run the Maven test/build flow:

```powershell
.\mvnw.cmd clean verify
```

## Core workflow API

The orchestrator exposes workflow endpoints under `/api/v1/workflows`.

### 1. Submit a requirement

```powershell
$body = @{ requirement = "Create a URL shortener with redirect behavior and tests."; repositoryPath = "url-shortener" } | ConvertTo-Json
$submitted = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/workflows" -ContentType "application/json" -Body $body
$submitted.workflowId
```

- `POST /api/v1/workflows` accepts a requirement and repository path
- It creates revision 1 and begins requirement interpretation asynchronously
- Ambiguous requirements pause in `AWAITING_CLARIFICATION`

### 2. Clarify an ambiguous requirement

```powershell
$clarification = @{
  clarifiedRequirement = "Create a URL shortener with case-sensitive custom aliases, redirect behavior, expiry handling, UTC analytics, and tests."
  answers = @{ "purpose" = "Customer-facing short links"; "alias" = "case-sensitive custom alias" }
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/workflows/$workflowId/clarifications" `
  -ContentType "application/json" -Headers @{ "X-Operator-Id" = "demo-operator"; "X-Operator-Token" = "local-operator-token" } `
  -Body $clarification
```

### 3. Create a repository plan

Once the requirement is clear, the workflow can move into planning:

```powershell
$plan = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/workflows/$workflowId/plan"
$plan.status
$plan.planHash
$plan.plan.tasks
```

The planning step copies the repository into an isolated workspace, records a baseline manifest, analyzes structure/data flow, and persists a dependency plan before the workflow pauses for approval.

### 4. Apply the generated patch

After review and approval:

```powershell
$applyBody = @{ planHash = $plan.planHash } | ConvertTo-Json
$changes = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/workflows/$workflowId/changes/apply" `
  -ContentType "application/json" -Body $applyBody

$changes.status
$changes.changedPaths
```

Only validated model-generated file operations are applied. There is no direct patching path outside the governed proposal engine.

### 5. Validate and approve outcome

```powershell
$validation = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/workflows/$workflowId/validate"
$outcome = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/workflows/$workflowId/outcome"
```

Change approval and release approval require exact evidence hashes and matching operator tokens:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/workflows/$workflowId/approvals/change" `
  -ContentType "application/json" -Headers @{ "X-Change-Approver-Token" = "local-change-approver-token"; "X-Approver-Id" = "demo-change-approver" } `
  -Body (@{ evidenceHash = $plan.planHash } | ConvertTo-Json)

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/workflows/$workflowId/approvals/release" `
  -ContentType "application/json" -Headers @{ "X-Release-Approver-Token" = "local-release-approver-token"; "X-Approver-Id" = "demo-release-approver" } `
  -Body (@{ evidenceHash = $outcome.outcomeHash } | ConvertTo-Json)
```

## Built-in demo scenarios

The repository includes a PowerShell script to run common workflow scenarios:

```powershell
.\demo.ps1 greenfield
.\demo.ps1 brownfield
.\demo.ps1 ambiguous
.\demo.ps1 repair
.\demo.ps1 safe-stop
.\demo.ps1 failover
```

These scenarios exercise:

- greenfield project generation
- brownfield enhancement of an existing codebase
- clarification flow for ambiguous requirements
- bounded repair after a controlled validation failure
- safe cancellation
- failover across the second orchestrator instance

## Docker Compose topology

`compose.yaml` starts:

- a PostgreSQL container
- `orchestrator-1` on port `8080`
- `orchestrator-2` on port `8081`

Both orchestrators connect to the same PostgreSQL instance and shared workspace volume. This demonstrates durable coordination and failover behavior across multiple nodes.

```powershell
docker compose config --quiet
docker compose up -d --build
```

## Production and security notes

- Local mode uses HTTP Basic auth and role-based access control
- `prod` mode switches to OIDC/JWT role mapping via `agentic.security.local.*` replacement and the `roles` claim
- Repository access is constrained to approved roots and blocks traversal, symlinks, unsupported files, and limit violations
- Model output is untrusted and cannot directly mutate the repository or run commands
- Validation runs the fixed Maven wrapper only, with secrets stripped from subprocess environments and bounded output capture

## Documentation

See the project docs for deeper operational detail:

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/SCENARIOS.md](docs/SCENARIOS.md)
- [docs/REVIEWER-GUIDE.md](docs/REVIEWER-GUIDE.md)
- [docs/OBSERVABILITY.md](docs/OBSERVABILITY.md)
- [docs/PRODUCTION-DEPLOYMENT.md](docs/PRODUCTION-DEPLOYMENT.md)
- [docs/TRACEABILITY.md](docs/TRACEABILITY.md)

## Notes

This project is intentionally designed around evidence-driven engineering: workflow state, repository analysis, plans, proposals, validation attempts, and approvals are all persisted and traceable. The goal is to keep generated changes and human approvals tied to exact hashes rather than trusting opaque model output.
