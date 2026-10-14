// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.type.LogicalType;

/** Entry point for the Spring Boot backend and its startup components. */
@SpringBootApplication
public class Main {

    /** Creates the application configuration used by Spring Boot. */
    public Main() {}

    @Bean
    ServerHttpMessageConvertersCustomizer snapshotJson() {
        // Keep snapshots complete both directly and inside Java response records.
        return builder -> builder.configureMessageConvertersList(
                converters -> converters.removeIf(KotlinSerializationJsonHttpMessageConverter.class::isInstance));
    }

    @Bean
    JsonMapperBuilderCustomizer commandJson() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .withCoercionConfig(
                        LogicalType.Textual,
                        coercion -> coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }

    /**
     * Starts the backend application, including the board initializer and HTTP server.
     *
     * @param args the command-line arguments passed to Spring Boot
     */
    public static void main(final String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
