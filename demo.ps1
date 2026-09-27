param(
    [ValidateSet("greenfield", "brownfield", "ambiguous", "repair", "safe-stop", "failover")]
    [string]$Scenario,
    [string]$BaseUrl = "http://localhost:8080"
)
$ErrorActionPreference = "Stop"
$credential = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("operator:local-development-only"))
$headers = @{ Authorization = "Basic $credential" }

function Wait-Ready {
    $deadline = (Get-Date).AddMinutes(2)
    do {
        try {
            $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/readiness" -TimeoutSec 5
            if ($health.status -eq "UP") { Write-Host "Orchestrator ready:" $BaseUrl; return }
        } catch { Write-Host "Waiting for orchestrator readiness at $BaseUrl..." }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Orchestrator did not become ready within two minutes: $BaseUrl"
}

function Invoke-Agentic($Method, $Path, $Body = $null, $Extra = @{}) {
    $all = $headers.Clone(); foreach ($key in $Extra.Keys) { $all[$key] = $Extra[$key] }
    $args = @{ Method=$Method; Uri="$BaseUrl$Path"; Headers=$all }
    if ($null -ne $Body) { $args.ContentType="application/json"; $args.Body=($Body | ConvertTo-Json -Depth 8) }
    Invoke-RestMethod @args
}
function Wait-State($Id, [string[]]$States) {
    do { Start-Sleep 1; $workflow=Invoke-Agentic Get "/api/v1/workflows/$Id"; Write-Host "Persisted state:" $workflow.status }
    until ($States -contains $workflow.status)
    $workflow
}

$requirements = @{
    greenfield = "Create a runnable URL shortener. POST /urls returns HTTP 201; GET /{code} redirects with HTTP 302. Accept case-sensitive custom aliases and reject duplicates with HTTP 409. Accept expiry as an ISO-8601 UTC instant and return HTTP 410 after expiry. Record total and UTC daily analytics. Generate unit and HTTP tests."
    brownfield = "Enhance the URL shortener. POST /urls returns HTTP 201; GET /{code} redirects with HTTP 302. Accept case-sensitive custom aliases and reject duplicates with HTTP 409. Accept expiry as an ISO-8601 UTC instant and return HTTP 410 after expiry. Record total and UTC daily analytics. Generate unit and HTTP tests."
    ambiguous = "Please make links better for customers."
    repair = "Create a URL shortener with POST /urls returning HTTP 201 and GET /{code} redirecting with HTTP 302. Use case-sensitive aliases with HTTP 409 for duplicates, ISO-8601 UTC expiry with HTTP 410, UTC daily analytics, and tests; run the repair scenario."
    "safe-stop" = "Create a URL shortener with POST /urls returning HTTP 201 and GET /{code} redirecting with HTTP 302, plus generated tests."
    failover = "Create a URL shortener with POST /urls returning HTTP 201 and GET /{code} redirecting with HTTP 302. Use case-sensitive aliases with HTTP 409 for duplicates, ISO-8601 UTC expiry with HTTP 410, UTC daily analytics, and tests."
}
$repository = if ($Scenario -eq "greenfield") { "greenfield-seed" } else { "url-shortener" }
Wait-Ready
$submitted = Invoke-Agentic Post "/api/v1/workflows" @{ requirement=$requirements[$Scenario]; repositoryPath=$repository }
Write-Host "Workflow:" $submitted.workflowId

if ($Scenario -eq "safe-stop") {
    $result=Invoke-Agentic Post "/api/v1/workflows/$($submitted.workflowId)/cancel" $null @{
        "X-Operator-Token"="local-operator-token"; "X-Operator-Id"="demo-operator" }
    $result | ConvertTo-Json -Depth 8; exit
}
if ($Scenario -eq "ambiguous") {
    $paused=Wait-State $submitted.workflowId @("AWAITING_CLARIFICATION")
    $answers=@{}; foreach ($q in $paused.analysis.questions) { $answers[$q.key]="Use case-sensitive aliases, expiry, and UTC daily analytics." }
    $clarified = "Create a URL shortener with case-sensitive custom aliases, expiry, redirects, UTC daily analytics, validation, unit tests, and HTTP tests."
    Invoke-Agentic Post "/api/v1/workflows/$($submitted.workflowId)/clarifications" @{
        clarifiedRequirement=$clarified; answers=$answers } @{
        "X-Operator-Token"="local-operator-token"; "X-Operator-Id"="demo-operator" } | Out-Null
}
$ready=Wait-State $submitted.workflowId @("PLANNING")
$plan=Invoke-Agentic Post "/api/v1/workflows/$($submitted.workflowId)/plan"
Write-Host "Plan hash:" $plan.planHash "Agent invocations:" $plan.agentInvocations.Count
Invoke-Agentic Post "/api/v1/workflows/$($submitted.workflowId)/approvals/change" @{ evidenceHash=$plan.planHash } @{
    "X-Change-Approver-Token"="local-change-approver-token"; "X-Approver-Id"="demo-change-approver" } | Out-Null
$changes=Invoke-Agentic Post "/api/v1/workflows/$($submitted.workflowId)/changes/apply" @{ planHash=$plan.planHash }
Write-Host "Generated paths:" ($changes.changedPaths -join ", ")
if ($Scenario -eq "failover") {
    Write-Host "Stop orchestrator-1 now; durable state can be read through port 8081."
    $BaseUrl="http://localhost:8081"
    Wait-Ready
}
$validation=Invoke-Agentic Post "/api/v1/workflows/$($submitted.workflowId)/validate"
Write-Host "Validation attempts:" $validation.attempts.Count "Status:" $validation.status
if ($validation.status -ne "AWAITING_RELEASE_APPROVAL") {
    foreach ($attempt in $validation.attempts) {
        Write-Host "Attempt $($attempt.attemptNumber): exit=$($attempt.build.exitCode) classification=$($attempt.build.classification) decision=$($attempt.decision)"
        if ($attempt.reason) { Write-Host "Reason:" $attempt.reason }
        foreach ($stream in @("stdout", "stderr")) {
            $content = [string]$attempt.build.$stream
            if ($content) {
                $tailLength = [Math]::Min(4000, $content.Length)
                Write-Host ($stream.ToUpper() + " tail:")
                Write-Host $content.Substring($content.Length - $tailLength)
            }
        }
    }
    throw "Validation did not pass. Rebuild the image after Dockerfile changes, then inspect the persisted attempt output above."
}
$outcome=Invoke-Agentic Post "/api/v1/workflows/$($submitted.workflowId)/outcome"
Write-Host "Outcome hash:" $outcome.outcomeHash "Release ready:" $outcome.releaseReady
Invoke-Agentic Post "/api/v1/workflows/$($submitted.workflowId)/approvals/release" @{ evidenceHash=$outcome.outcomeHash } @{
    "X-Release-Approver-Token"="local-release-approver-token"; "X-Approver-Id"="demo-release-approver" } | ConvertTo-Json -Depth 8
