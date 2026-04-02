#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${1:?Usage: deploy-cloudrun.sh <project-id> [region] [service-name]}"
REGION="${2:-us-central1}"
SERVICE_NAME="${3:-harmony-web}"
IMAGE="gcr.io/${PROJECT_ID}/${SERVICE_NAME}"

echo "Using project: ${PROJECT_ID}"
echo "Using region: ${REGION}"
echo "Using service: ${SERVICE_NAME}"

gcloud config set project "${PROJECT_ID}"
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

echo "Building container image..."
gcloud builds submit . --tag "${IMAGE}"

echo "Deploying to Cloud Run..."
gcloud run deploy "${SERVICE_NAME}" \
  --image "${IMAGE}" \
  --platform managed \
  --region "${REGION}" \
  --allow-unauthenticated \
  --port 8090 \
  --memory 1Gi \
  --cpu 1 \
  --concurrency 1 \
  --max-instances 1 \
  --set-env-vars PORT=8090

echo "Deployment complete."
