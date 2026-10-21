# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

FROM nginxinc/nginx-unprivileged:1.27-alpine-slim@sha256:ec6b8b1f1bfa594db13e99fc692b9fb816cb7e365927196610f622659de95913

LABEL org.opencontainers.image.title="Sticky Notes Web" \
      org.opencontainers.image.description="Nginx static web server reverse proxy for Sticky Notes" \
      org.opencontainers.image.licenses="MIT"

USER 101

COPY --chown=101:101 frontend/nginx.conf /etc/nginx/nginx.conf
COPY --chown=101:101 frontend/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=5s --timeout=3s --retries=5 --start-period=3s \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/index.html || exit 1

STOPSIGNAL SIGQUIT

CMD ["nginx", "-g", "daemon off;"]
