// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes;

import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter;
import stickynotes.api.dto.BlockNoteRequest;
import stickynotes.api.dto.CreateNoteRequest;
import stickynotes.api.dto.MoveNoteRequest;
import stickynotes.api.dto.SetWipLimitRequest;
import stickynotes.api.dto.UpdateNoteRequest;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.impl.NullsFailProvider;
import tools.jackson.databind.module.SimpleModule;
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
        var requests = Set.of(
                CreateNoteRequest.class,
                UpdateNoteRequest.class,
                MoveNoteRequest.class,
                SetWipLimitRequest.class,
                BlockNoteRequest.class);
        var nulls = new SimpleModule().setDeserializerModifier(new ValueDeserializerModifier() {
            @Override
            public BeanDeserializerBuilder updateBuilder(
                    DeserializationConfig config,
                    BeanDescription.Supplier description,
                    BeanDeserializerBuilder builder) {
                if (requests.contains(description.getBeanClass())) {
                    var properties = builder.getProperties();
                    while (properties.hasNext()) {
                        var property = properties.next();
                        builder.addOrReplaceProperty(
                                property.withNullProvider(
                                        new NullsFailProvider(property.getFullName(), property.getType()) {
                                            @Override
                                            public Object getAbsentValue(DeserializationContext context) {
                                                // Omitted PATCH fields and the default creation color remain valid.
                                                return null;
                                            }
                                        }),
                                true);
                    }
                }
                return builder;
            }
        });
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .withCoercionConfig(
                        LogicalType.Textual,
                        coercion -> coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .addModule(nulls);
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
