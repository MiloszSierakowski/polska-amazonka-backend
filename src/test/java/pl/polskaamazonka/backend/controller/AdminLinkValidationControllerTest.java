package pl.polskaamazonka.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminLinkValidationControllerTest {

    @Test
    void controllerExplicitlyAllowsAdminAndWorkerRoles() {
        PreAuthorize authorization = AdminLinkValidationController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertEquals("hasAnyRole('ADMIN', 'WORKER')", authorization.value());
    }
}
