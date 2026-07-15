<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# Architecture

`commons` owns the domain model and compiles for JVM and JavaScript. `backend` is a Java HTTP adapter. `frontend` is a Vue adapter. The domain must not depend on HTTP, Vue, or persistence.
