param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectId,
    [string]$Region = "us-central1",
    [string]$ServiceName = "harmony-web"
)

$ErrorActionPreference = "Stop"

$image = "gcr.io/$ProjectId/$ServiceName"

function Invoke-GCloud {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & gcloud @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "gcloud command failed with exit code ${LASTEXITCODE}: gcloud $($Arguments -join ' ')"
    }
}

Write-Host "Using project: $ProjectId"
Write-Host "Using region: $Region"
Write-Host "Using service: $ServiceName"

Invoke-GCloud -Arguments @("config", "set", "project", $ProjectId)
Invoke-GCloud -Arguments @("services", "enable", "run.googleapis.com", "cloudbuild.googleapis.com", "artifactregistry.googleapis.com")

Write-Host "Building container image..."
Invoke-GCloud -Arguments @("builds", "submit", ".", "--tag", $image)

Write-Host "Deploying to Cloud Run..."
Invoke-GCloud -Arguments @(
    "run", "deploy", $ServiceName,
    "--image", $image,
    "--platform", "managed",
    "--region", $Region,
    "--allow-unauthenticated",
    "--port", "8090",
    "--memory", "1Gi",
    "--cpu", "1",
    "--concurrency", "1",
    "--max-instances", "1",
    "--set-env-vars", "PORT=8090"
)

Write-Host "Deployment complete."
