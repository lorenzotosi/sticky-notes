// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the Spring Boot backend and its startup components. */
@SpringBootApplication
public class Main {

    /** Creates the application configuration used by Spring Boot. */
    public Main() {}

    /**
     * Starts the backend application, including the board initializer and HTTP server.
     *
     * @param args the command-line arguments passed to Spring Boot
     */
    public static void main(final String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
