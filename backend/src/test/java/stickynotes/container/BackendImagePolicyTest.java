// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Guards the contract of the backend runtime image: a base image pinned to a
 * digest, a single copied artifact, an unprivileged process and an entrypoint
 * that receives the stop signal. These are the properties of the image that a
 * manual inspection would miss once the image exists.
 */
class BackendImagePolicyTest {

    private static final Path DOCKERFILE = repositoryFile("backend/Dockerfile");

    @Test
    void baseImageIsPinnedToADigest() {
        final String from = instruction("FROM");

        assertTrue(from.contains("@sha256:"), "The base image must be pinned to a digest: " + from);
    }

    @Test
    void applicationRunsAsAnUnprivilegedUser() {
        final String user = instruction("USER");

        assertFalse(user.isBlank(), "The image must declare the user running the application.");
        assertNotEquals("root", user.trim(), "The application must not run as root.");
    }

    @Test
    void imageCopiesOnlyTheVerifiedJar() {
        final List<String> copies = instructions("COPY");

        assertEquals(1, copies.size(), "The image must copy the verified JAR only: " + copies);
        assertTrue(
                copies.get(0).contains("backend/build/libs/backend.jar"),
                "The image must copy the JAR produced by the build: " + copies);
    }

    @Test
    void stopSignalReachesTheApplication() {
        assertEquals("SIGTERM", instruction("STOPSIGNAL"), "The image must declare the stop signal.");
        assertTrue(
                instruction("ENTRYPOINT").startsWith("["),
                "The entrypoint must run in exec form, so the JVM receives the stop signal: "
                        + instruction("ENTRYPOINT"));
    }

    private static String instruction(final String keyword) {
        final List<String> values = instructions(keyword);
        assertEquals(1, values.size(), "Expected exactly one " + keyword + " instruction: " + values);
        return values.get(0);
    }

    private static List<String> instructions(final String keyword) {
        return dockerfileInstructions().stream()
                .filter(line -> line.startsWith(keyword + " "))
                .map(line -> line.substring(keyword.length()).trim())
                .collect(Collectors.toList());
    }

    /** Reads the Dockerfile, folding the line continuations into single instructions. */
    private static List<String> dockerfileInstructions() {
        final List<String> instructions = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        try {
            for (final String rawLine : Files.readAllLines(DOCKERFILE)) {
                final String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.endsWith("\\")) {
                    current.append(line, 0, line.length() - 1).append(' ');
                    continue;
                }
                current.append(line);
                instructions.add(current.toString());
                current.setLength(0);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return instructions;
    }

    /** Finds a repository file from the working directory of the test, whatever it is. */
    private static Path repositoryFile(final String relativePath) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.exists(directory.resolve(relativePath))) {
            directory = directory.getParent();
        }
        if (directory == null) {
            throw new IllegalStateException("Cannot find " + relativePath + " from the working directory.");
        }
        return directory.resolve(relativePath);
    }
}
