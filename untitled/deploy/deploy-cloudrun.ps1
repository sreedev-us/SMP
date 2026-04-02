param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectId,
    [string]$Region = "us-central1",
    [string]$ServiceName = "harmony-web"
)

$ErrorActionPreference = "Stop"

$image = "gcr.io/$ProjectId/$ServiceName"

Write-Host "Using project: $ProjectId"
Write-Host "Using region: $Region"
Write-Host "Using service: $ServiceName"

gcloud config set project $ProjectId
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

Write-Host "Building container image..."
gcloud builds submit . --tag $image

Write-Host "Deploying to Cloud Run..."
gcloud run deploy $ServiceName `
  --image $image `
  --platform managed `
  --region $Region `
  --allow-unauthenticated `
  --port 8090 `
  --memory 1Gi `
  --cpu 1 `
  --concurrency 1 `
  --max-instances 1 `
  --set-env-vars PORT=8090

Write-Host "Deployment complete."
