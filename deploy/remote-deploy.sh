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
ATTU_BIND_IP="${ATTU_BIND_IP:-127.0.0.1}"
ATTU_PORT="${ATTU_PORT:-8000}"
BRANCH_NAME="${BRANCH_NAME:-main}"

resolve_volume_root() {
  if [ -n "${DOCKER_VOLUME_DIRECTORY:-}" ]; then
    echo "${DOCKER_VOLUME_DIRECTORY}"
  else
    # Keep consistent with docker-compose fallback ${DOCKER_VOLUME_DIRECTORY:-.}
    echo "$DEPLOY_PATH"
  fi
}

resolve_spring_user_ids() {
  # Resolve runtime uid/gid from image so host permissions match container user.
  SPRING_UID="$(docker run --rm --entrypoint sh "$IMAGE_TAG" -c 'id -u spring 2>/dev/null || id -u')"
  SPRING_GID="$(docker run --rm --entrypoint sh "$IMAGE_TAG" -c 'id -g spring 2>/dev/null || id -g')"
}

prepare_backend_logs_dir() {
  VOLUME_ROOT="$(resolve_volume_root)"
  LOG_DIR="${VOLUME_ROOT}/volumes/backend/logs"

  mkdir -p "$LOG_DIR"

  resolve_spring_user_ids

  # Run permission fix through Docker daemon to avoid requiring root shell user.
  docker run --rm -v "$LOG_DIR":/target alpine:3.20 sh -c \
    "chown -R ${SPRING_UID}:${SPRING_GID} /target && chmod -R ug+rwX /target"
}

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
require_env GRAFANA_ADMIN_PASSWORD

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

git fetch --prune origin "$BRANCH_NAME"
git reset --hard "origin/$BRANCH_NAME"
git clean -fdx

if git rev-parse --verify "$BRANCH_NAME" >/dev/null 2>&1; then
  git checkout "$BRANCH_NAME"
else
  git checkout -b "$BRANCH_NAME" "origin/$BRANCH_NAME"
fi

cat > .env.production <<EOF
APP_IMAGE=${IMAGE_TAG}
APP_PORT=${APP_PORT}
NGINX_HTTP_PORT=${NGINX_HTTP_PORT:-80}
DOCKER_VOLUME_DIRECTORY=${DOCKER_VOLUME_DIRECTORY:-}
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-}
MYSQL_DATABASE=${MYSQL_DATABASE}
MYSQL_USER=${MYSQL_USER}
MYSQL_PASSWORD=${MYSQL_PASSWORD:-}
REDIS_PASSWORD=${REDIS_PASSWORD:-}
JWT_SECRET=${JWT_SECRET:-}
API_KEY_ENCRYPT_KEY=${API_KEY_ENCRYPT_KEY:-}
REQUEST_DECRYPT_PRIVATE_KEY=${REQUEST_DECRYPT_PRIVATE_KEY:-}
REQUEST_ENCRYPT_PUBLIC_KEY=${REQUEST_ENCRYPT_PUBLIC_KEY:-}
APP_VECTORSTORE_INIT=${APP_VECTORSTORE_INIT:-false}
APP_AI_OPENAI_CHAT_BASE_URL=${APP_AI_OPENAI_CHAT_BASE_URL:-https://api.openai.com}
APP_AI_SILICONFLOW_EMBEDDING_API_KEY=${APP_AI_SILICONFLOW_EMBEDDING_API_KEY:-}
ATTU_BIND_IP=${ATTU_BIND_IP}
ATTU_PORT=${ATTU_PORT}
SPRING_MAIL_USERNAME=${SPRING_MAIL_USERNAME:-}
SPRING_MAIL_PASSWORD=${SPRING_MAIL_PASSWORD:-}
SPRING_AI_VECTORSTORE_MILVUS_DATABASE_NAME=${SPRING_AI_VECTORSTORE_MILVUS_DATABASE_NAME:-}
SPRING_AI_VECTORSTORE_MILVUS_COLLECTION_NAME=${SPRING_AI_VECTORSTORE_MILVUS_COLLECTION_NAME:-}
APP_ALIYUN_ACCESS_KEY_ID=${APP_ALIYUN_ACCESS_KEY_ID:-}
APP_ALIYUN_ACCESS_KEY_SECRET=${APP_ALIYUN_ACCESS_KEY_SECRET:-}
APP_ALIYUN_ACCOUNT_NAME=${APP_ALIYUN_ACCOUNT_NAME:-}
APP_ALIYUN_REGION_ID=${APP_ALIYUN_REGION_ID:-}
PROMETHEUS_PORT=${PROMETHEUS_PORT:-9090}
GRAFANA_ADMIN_USER=${GRAFANA_ADMIN_USER:-admin}
GRAFANA_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD:-}
GRAFANA_PORT=${GRAFANA_PORT:-3000}
EOF

chmod 600 .env.production

prepare_backend_logs_dir

docker compose --env-file .env.production -f docker-compose.yml pull
docker compose --env-file .env.production -f docker-compose.yml up -d --remove-orphans
docker image prune -f

