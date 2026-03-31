#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/harmony-pro"
REPO_URL="${1:-https://github.com/sreedev-us/SMP.git}"
BRANCH="${2:-main}"

echo "Preparing server packages..."
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin nginx curl

echo "Enabling Docker..."
sudo systemctl enable --now docker

if [ ! -d "$APP_DIR/.git" ]; then
  echo "Cloning repository into $APP_DIR"
  sudo git clone --branch "$BRANCH" "$REPO_URL" "$APP_DIR"
else
  echo "Updating existing repository in $APP_DIR"
  sudo git -C "$APP_DIR" fetch origin
  sudo git -C "$APP_DIR" checkout "$BRANCH"
  sudo git -C "$APP_DIR" pull origin "$BRANCH"
fi

cd "$APP_DIR/untitled"

echo "Building and starting standalone web app..."
sudo docker compose up -d --build

echo "Installing nginx site config..."
sudo cp deploy/nginx.conf /etc/nginx/sites-available/harmony-web
if [ ! -L /etc/nginx/sites-enabled/harmony-web ]; then
  sudo ln -s /etc/nginx/sites-available/harmony-web /etc/nginx/sites-enabled/harmony-web
fi
sudo nginx -t
sudo systemctl restart nginx

echo "Done. Update server_name in /etc/nginx/sites-available/harmony-web and add TLS with certbot."
