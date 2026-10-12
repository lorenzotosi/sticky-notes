// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
public class MongoConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    MongoClientSettingsBuilderCustomizer mongoTimeouts() {
        return builder -> {
            builder.applyToClusterSettings(settings -> settings.serverSelectionTimeout(5, TimeUnit.SECONDS));
            builder.applyToSocketSettings(settings -> settings.connectTimeout(5, TimeUnit.SECONDS));
            builder.timeout(5, TimeUnit.SECONDS);
        };
    }
}
