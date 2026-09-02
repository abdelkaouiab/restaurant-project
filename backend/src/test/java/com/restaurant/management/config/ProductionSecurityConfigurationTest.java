package com.restaurant.management.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSecurityConfigurationTest {

    @Test
    void profilProductionForceLesOptionsDeSecurite() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application-prod", new ClassPathResource("application-prod.yml")
        );

        assertThat(valeur(sources, "server.servlet.session.cookie.secure")).isEqualTo(true);
        assertThat(valeur(sources, "server.servlet.session.cookie.http-only")).isEqualTo(true);
        assertThat(valeur(sources, "server.servlet.session.cookie.same-site")).isEqualTo("strict");
        assertThat(valeur(sources, "springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(valeur(sources, "springdoc.swagger-ui.enabled")).isEqualTo(false);
        assertThat(valeur(sources, "management.server.address"))
                .isEqualTo("${MANAGEMENT_SERVER_ADDRESS:127.0.0.1}");
        assertThat(valeur(sources, "app.security.cors.allowed-origins"))
                .isEqualTo("${APP_CORS_ALLOWED_ORIGINS}");
        assertThat(valeur(sources, "spring.flyway.clean-disabled")).isEqualTo(true);
    }

    private Object valeur(List<PropertySource<?>> sources, String nom) {
        return sources.stream()
                .map(source -> source.getProperty(nom))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
