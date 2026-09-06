package com.example.payment.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(com.example.payment.SecurityConfig.class)
class PaymentSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    com.example.payment.application.ProcessPaymentUseCase processPayment;

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminIsForbiddenFromPayments() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerPassesSecurity() throws Exception {
        // Security passes; invalid body gives 400.
        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
