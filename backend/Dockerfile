# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

# Runtime image of the Sticky Notes backend.
#
# The JAR is built by Gradle before the image is assembled
# (`:backend:dockerImage`), so the image carries the application only: no
# Gradle runtime, no sources, no build cache. The base image is pinned to a
# digest, so the runtime does not change under an unchanged tag.

FROM eclipse-temurin:21-jre@sha256:227118764cccd3c3294617061aaa93a08309c63a395eefa83e6b35032992ea99

# The version and the commit are supplied by the build and stored as labels,
# so a candidate image can be traced back to the source it was produced from.
ARG VERSION=0.0.0-dev
ARG REVISION=unknown
ARG SOURCE=https://github.com/lorenzotosi/sticky-notes

LABEL org.opencontainers.image.title="Sticky Notes backend" \
      org.opencontainers.image.description="Spring Boot REST API of the Sticky Notes Kanban board" \
      org.opencontainers.image.source="${SOURCE}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.licenses="MIT"

# The application owns an unprivileged account. It writes only to the shared
# temporary directory inherited from the base image and to its own /app
# directory.
RUN groupadd --gid 10001 sticky-notes \
    && useradd --uid 10001 --gid sticky-notes --home-dir /app --shell /usr/sbin/nologin --no-create-home sticky-notes \
    && mkdir -p /app \
    && chown sticky-notes:sticky-notes /app

WORKDIR /app
COPY --chown=sticky-notes:sticky-notes backend/build/libs/backend.jar /app/backend.jar

USER sticky-notes

EXPOSE 8080

# Readiness reports the application and the database it needs. The check runs
# inside the container, so it never depends on an external client.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl --fail --silent --show-error "http://127.0.0.1:${PORT:-8080}/actuator/health/readiness" || exit 1

# `docker stop` sends SIGTERM. The entrypoint runs in exec form, so the JVM is
# the first process and receives the signal directly.
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
