package com.example.order.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the resource-server rules: no token -> 401, wrong role -> 403,
 * valid role -> passes the filter chain (may still 4xx on business grounds).
 */
@WebMvcTest(com.example.order.adapter.in.web.OrderController.class)
@Import(com.example.order.SecurityConfig.class)
class OrderSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    com.example.order.application.CreateOrderUseCase createOrder;

    @MockBean
    com.example.order.application.GetOrderUseCase getOrder;

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotCreateOrders() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerPassesSecurityForCreate() throws Exception {
        // Security passes; body is invalid so we expect 400, not 401/403.
        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
