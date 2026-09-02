#!/bin/sh

set -eu

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIRECTORY"

POSTGRES_CONTAINER_NAME=${POSTGRES_CONTAINER_NAME:-restaurant-postgres}

container_environment_value() {
    key=$1
    docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$POSTGRES_CONTAINER_NAME" |
        awk -v prefix="$key=" 'index($0, prefix) == 1 { sub(/^[^=]*=/, ""); print; exit }'
}

if [ -z "${DB_USERNAME:-}" ] || [ -z "${DB_NAME:-}" ] || [ -z "${DB_PASSWORD:-}" ]; then
    if ! command -v docker >/dev/null 2>&1; then
        echo "Docker est requis pour lire la configuration PostgreSQL locale." >&2
        exit 1
    fi

    if ! docker inspect "$POSTGRES_CONTAINER_NAME" >/dev/null 2>&1; then
        echo "Le conteneur PostgreSQL '$POSTGRES_CONTAINER_NAME' est introuvable." >&2
        exit 1
    fi

    DB_USERNAME=${DB_USERNAME:-$(container_environment_value POSTGRES_USER)}
    DB_NAME=${DB_NAME:-$(container_environment_value POSTGRES_DB)}
    DB_PASSWORD=${DB_PASSWORD:-$(container_environment_value POSTGRES_PASSWORD)}
fi

if [ -z "$DB_USERNAME" ] || [ -z "$DB_NAME" ] || [ -z "$DB_PASSWORD" ]; then
    echo "La configuration PostgreSQL locale est incomplète." >&2
    exit 1
fi

export DB_USERNAME DB_NAME DB_PASSWORD
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev}

exec ./mvnw spring-boot:run "$@"
