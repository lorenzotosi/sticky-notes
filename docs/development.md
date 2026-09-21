<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# Sviluppo locale

## Toolchain

- JDK 21 per il progetto Java e Kotlin.
- Gradle 9.7.1 tramite il wrapper ufficiale `./gradlew`.
- Node.js 24.21.0, fissato in `.nvmrc`, compatibile con semantic-release 25.0.3 e Vite.

Il repository usa il wrapper Gradle già aggiornato nella baseline corrente. La
distribuzione binaria è verificata da `distributionSha256Sum` nel file
`gradle/wrapper/gradle-wrapper.properties`.

## Bootstrap della shell

Impostare JDK 21 solo nella shell corrente, senza modificare le variabili
globali della macchina:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
java -version
./gradlew --version
```

Su Windows, impostare `JAVA_HOME` alla directory del JDK 21 nella sessione
PowerShell prima di eseguire `./gradlew.bat --version`.

Per Node.js, usare un gestore di versioni che legga `.nvmrc`, quindi verificare:

```sh
nvm use
node --version
npm --version
```

La prova minima della toolchain deve mostrare Java 21 nella sezione `Daemon
JVM` di Gradle e Node.js `v24.21.0`.
