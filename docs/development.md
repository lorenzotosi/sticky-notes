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

## Dipendenze Gradle

Le versioni condivise sono in `gradle/libs.versions.toml`. I lock Gradle sono
generati per i classpath di compilazione, runtime e test dell'applicazione JVM:

```sh
./gradlew :commons:dependencies :backend:dependencies --write-locks
```

Eseguire poi lo stesso comando senza `--write-locks`: i file di lock non devono
cambiare. Il lock npm del frontend resta `frontend/package-lock.json`; il lock
Kotlin/JS resta `kotlin-js-store/yarn.lock` ed è gestito dai task Kotlin/JS.

I classpath interni del compilatore Kotlin, di Detekt, dei plugin Gradle e degli
strumenti Kotlin Multiplatform sono esclusi intenzionalmente dai lock di
progetto. Le versioni dirette dei plugin sono comunque fissate nel catalogo.
Non forzare la stdlib Kotlin usata internamente da Gradle o da Detekt alla
versione dell'applicazione.

Per controllare la stdlib risolta dal runtime applicativo:

```sh
./gradlew :commons:dependencyInsight \
  --configuration jvmRuntimeClasspath \
  --dependency org.jetbrains.kotlin:kotlin-stdlib
```
