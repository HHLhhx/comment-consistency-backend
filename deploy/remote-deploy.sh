#!/usr/bin/env sh

set -eu

if [ -z "${REPOSITORY:-}" ]; then
  echo "REPOSITORY is required"
  exit 1
fi

if [ -z "${IMAGE_TAG:-}" ]; then
  echo "IMAGE_TAG is required"
  exit 1
fi

DEPLOY_PATH="${DEPLOY_PATH:-}"
if [ -z "$DEPLOY_PATH" ]; then
  DEPLOY_PATH="${DEFAULT_DEPLOY_PATH:-/opt/comment-consistency-backend}"
fi

APP_PORT="${APP_PORT:-8080}"
MYSQL_DATABASE="${MYSQL_DATABASE:-comment_consistency}"
MYSQL_USER="${MYSQL_USER:-comment_user}"
APP_AI_OLLAMA_CHAT_BASE_URL="${APP_AI_OLLAMA_CHAT_BASE_URL:-https://ollama.com}"
APP_AI_OLLAMA_EMBEDDING_BASE_URL="${APP_AI_OLLAMA_EMBEDDING_BASE_URL:-http://ollama:11434}"
APP_AI_OLLAMA_EMBEDDING_MODEL="${APP_AI_OLLAMA_EMBEDDING_MODEL:-qwen3-embedding:0.6b}"

require_env() {
  var_name="$1"
  eval "var_value=\${$var_name:-}"
  if [ -z "$var_value" ]; then
    echo "$var_name is required"
    exit 1
  fi
}

require_env MYSQL_ROOT_PASSWORD
require_env MYSQL_PASSWORD
require_env REDIS_PASSWORD
require_env JWT_SECRET
require_env API_KEY_ENCRYPT_KEY

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is not installed on remote server"
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose plugin is not available on remote server"
  exit 1
fi

if ! command -v git >/dev/null 2>&1; then
  echo "git is not installed on remote server"
  exit 1
fi

mkdir -p "$DEPLOY_PATH"
if [ ! -d "$DEPLOY_PATH/.git" ]; then
  git clone "https://github.com/${REPOSITORY}.git" "$DEPLOY_PATH"
fi

cd "$DEPLOY_PATH"
git fetch origin main
git checkout main
git pull --ff-only origin main

cat > .env.production <<EOF
APP_IMAGE=${IMAGE_TAG}
APP_PORT=${APP_PORT}
DOCKER_VOLUME_DIRECTORY=${DOCKER_VOLUME_DIRECTORY:-}
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-}
MYSQL_DATABASE=${MYSQL_DATABASE}
MYSQL_USER=${MYSQL_USER}
MYSQL_PASSWORD=${MYSQL_PASSWORD:-}
REDIS_PASSWORD=${REDIS_PASSWORD:-}
JWT_SECRET=${JWT_SECRET:-}
API_KEY_ENCRYPT_KEY=${API_KEY_ENCRYPT_KEY:-}
APP_AI_OLLAMA_CHAT_BASE_URL=${APP_AI_OLLAMA_CHAT_BASE_URL}
APP_AI_OLLAMA_EMBEDDING_BASE_URL=${APP_AI_OLLAMA_EMBEDDING_BASE_URL}
APP_AI_OLLAMA_EMBEDDING_MODEL=${APP_AI_OLLAMA_EMBEDDING_MODEL}
EOF

chmod 600 .env.production

docker compose --env-file .env.production -f docker-compose.yml pull
docker compose --env-file .env.production -f docker-compose.yml up -d --remove-orphans
docker image prune -f

