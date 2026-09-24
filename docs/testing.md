<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# Shared domain tests

`commons/src/commonTest` contains domain scenarios shared by the JVM and ChromeHeadless targets. `browserDomainTest` is an alias for `jsBrowserTest`.

Install JDK 21 and Google Chrome before running the browser tests. Point `CHROME_BIN` to the Chrome executable if Karma cannot find it:

```sh
# macOS
export CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# Linux (after installing Google Chrome)
export CHROME_BIN="$(command -v google-chrome)"

./gradlew :commons:jvmTest :commons:browserDomainTest
```

On Windows PowerShell, set `$env:CHROME_BIN` to the full path of `chrome.exe` (normally under `C:\Program Files\Google\Chrome\Application\`) and run `.\gradlew.bat :commons:jvmTest :commons:browserDomainTest`. Set the Gradle JVM in IntelliJ IDEA to JDK 21.

CI uses the Chrome installation on the [GitHub Actions Ubuntu runner](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md). The quality workflow checks `google-chrome --version`, exports `CHROME_BIN`, and runs `fullBuild`, which includes both common test targets. Compare `commons/build/test-results/jvmTest/` and `commons/build/test-results/jsBrowserTest/`: both must show the shared scenarios executed, with no skipped tests. The workflow uploads these reports even when a test fails.

To check that both targets detect a core regression, temporarily change an expected value in a shared domain test to an incorrect value. Run `./gradlew :commons:jvmTest :commons:browserDomainTest --rerun-tasks --continue` and confirm that test fails in both reports. Restore the assertion and rerun the command; both targets must pass.
