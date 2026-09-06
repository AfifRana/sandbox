package com.example.product.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(com.example.product.SecurityConfig.class)
class ProductSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    com.example.product.application.GetProductUseCase getProduct;

    @MockBean
    com.example.product.application.SaveProductUseCase saveProduct;

    @Test
    void writesRequireAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPassesSecurityForWrites() throws Exception {
        // Security passes; invalid body gives 400.
        mockMvc.perform(post("/api/v1/products")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putRequiresAdminToo() throws Exception {
        mockMvc.perform(put("/api/v1/products/00000000-0000-0000-0000-000000000000")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
