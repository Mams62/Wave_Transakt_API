#!/bin/sh
set -eu

if [ -n "${WAVE_DB_HOST:-}" ]; then
  export WAVE_DB_URL="jdbc:postgresql://${WAVE_DB_HOST}:${WAVE_DB_PORT:-5432}/${WAVE_DB_NAME}"
fi

exec java \
  -XX:MaxRAMPercentage=70.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /app/app.jar
