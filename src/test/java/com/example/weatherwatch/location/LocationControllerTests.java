package com.example.weatherwatch.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:weather;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.kafka.listener.auto-startup=false",
        "weather.kafka.create-topic=false"
})
class LocationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LocationEventPublisher eventPublisher;

    @BeforeEach
    void clearLocations() {
        locationRepository.deleteAll();
    }

    @Test
    void createsReadsUpdatesAndDeletesLocation() throws Exception {
        String createdJson = mockMvc.perform(post("/api/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Jakarta  ","latitude":-6.2,"longitude":106.8}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Jakarta"))
                .andExpect(jsonPath("$.latitude").value(-6.2))
                .andExpect(jsonPath("$.longitude").value(106.8))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createdJson);
        long id = created.get("id").asLong();
        verify(eventPublisher).publish(id, LocationEventType.CREATED);

        mockMvc.perform(get("/api/locations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Jakarta"));

        mockMvc.perform(put("/api/locations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bandung","latitude":-6.9,"longitude":107.6}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bandung"))
                .andExpect(jsonPath("$.latitude").value(-6.9))
                .andExpect(jsonPath("$.longitude").value(107.6));
        verify(eventPublisher).publish(id, LocationEventType.UPDATED);

        mockMvc.perform(delete("/api/locations/{id}", id))
                .andExpect(status().isNoContent());
        verify(eventPublisher).publish(id, LocationEventType.DELETED);

        mockMvc.perform(get("/api/locations/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Location not found"));

        assertThat(locationRepository.existsById(id)).isFalse();
    }

    @Test
    void listsLocationsInIdOrder() throws Exception {
        mockMvc.perform(post("/api/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"First","latitude":0,"longitude":0}
                        """)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Second","latitude":1,"longitude":1}
                        """)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("First"))
                .andExpect(jsonPath("$[1].name").value("Second"));
    }

    @Test
    void rejectsInvalidLocationWithoutPersistingIt() throws Exception {
        mockMvc.perform(post("/api/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalid","latitude":91,"longitude":181}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.detail").isNotEmpty());

        mockMvc.perform(post("/api/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   ","latitude":0,"longitude":0}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(locationRepository.count()).isZero();
    }

    @Test
    void returnsNotFoundForUnknownLocation() throws Exception {
        mockMvc.perform(get("/api/locations/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Location not found"));

        mockMvc.perform(put("/api/locations/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Missing","latitude":0,"longitude":0}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/locations/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rollsBackLocationCreationAndReturnsUnavailableWhenKafkaPublishFails() throws Exception {
        org.mockito.Mockito.doThrow(new LocationEventPublishException(
                        "Could not publish location event", new IllegalStateException("broker unavailable")))
                .when(eventPublisher).publish(org.mockito.ArgumentMatchers.anyLong(), eq(LocationEventType.CREATED));

        mockMvc.perform(post("/api/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No event","latitude":0,"longitude":0}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Location event service unavailable"));

        assertThat(locationRepository.count()).isZero();
    }
}
