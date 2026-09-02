#!/usr/bin/env sh

set -eu

script_directory=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_directory=$(CDPATH= cd -- "$script_directory/.." && pwd)
temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/restaurant-docker-smoke.XXXXXX")
compose_project="restaurant-smoke-$$"
test_succeeded=false

cleanup() {
    if [ "$test_succeeded" != "true" ]; then
        docker compose \
            --project-name "$compose_project" \
            --file "$project_directory/compose.yaml" \
            ps >&2 2>/dev/null || true
        docker compose \
            --project-name "$compose_project" \
            --file "$project_directory/compose.yaml" \
            logs --tail=150 backend frontend reverse-proxy postgres >&2 2>/dev/null || true
    fi
    docker compose \
        --project-name "$compose_project" \
        --file "$project_directory/compose.yaml" \
        down --volumes --remove-orphans >/dev/null 2>&1 || true
    rm -f "$temporary_directory/fullchain.pem" "$temporary_directory/privkey.pem"
    rmdir "$temporary_directory" 2>/dev/null || true
}

trap cleanup EXIT INT TERM

command -v docker >/dev/null 2>&1 || {
    echo "Docker est requis pour ce test." >&2
    exit 1
}

command -v openssl >/dev/null 2>&1 || {
    echo "OpenSSL est requis pour générer le certificat de test." >&2
    exit 1
}

command -v curl >/dev/null 2>&1 || {
    echo "curl est requis pour les sondes HTTP." >&2
    exit 1
}

openssl req \
    -x509 \
    -newkey rsa:3072 \
    -sha256 \
    -days 1 \
    -nodes \
    -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
    -keyout "$temporary_directory/privkey.pem" \
    -out "$temporary_directory/fullchain.pem" \
    >/dev/null 2>&1

export DB_PASSWORD
DB_PASSWORD=$(openssl rand -hex 24)
export TLS_CERTIFICATE_PATH="$temporary_directory/fullchain.pem"
export TLS_PRIVATE_KEY_PATH="$temporary_directory/privkey.pem"
export PROXY_UID
PROXY_UID=$(id -u)
export PROXY_GID
PROXY_GID=$(id -g)
export APP_BIND_ADDRESS=127.0.0.1
export APP_HTTP_PORT=18080
export APP_HTTPS_PORT=18443
export APP_PUBLIC_ORIGIN=https://localhost:18443

compose() {
    docker compose \
        --project-name "$compose_project" \
        --file "$project_directory/compose.yaml" \
        "$@"
}

compose config --quiet
compose build
compose up --detach --wait --wait-timeout 300

curl --fail --silent --show-error --insecure \
    https://127.0.0.1:18443/_health | grep -q UP
curl --fail --silent --show-error --insecure \
    https://127.0.0.1:18443/api/health | grep -q '"status":"UP"'
curl --fail --silent --show-error --insecure --head \
    https://127.0.0.1:18443/ | grep -qi '^strict-transport-security:'

compose ps
test_succeeded=true
echo "Test Docker complet réussi."
