package com.restaurant.management.controller;

import com.restaurant.management.RestaurantManagementApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(classes = RestaurantManagementApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublicAndMinimal() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void actuatorHealthRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/actuator/health"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void actuatorEstRefuseAUnUtilisateurNonAdministrateur() throws Exception {
        mockMvc.perform(get("/actuator/health").with(user("gerant").roles("GERANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiEstRefuseAUnUtilisateurNonAdministrateur() throws Exception {
        mockMvc.perform(get("/api-docs").with(user("gerant").roles("GERANT")))
                .andExpect(status().isForbidden());
    }
}
