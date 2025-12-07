package com.nju.comment.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void cors_preflight_should_allow_all_methods_and_headers_and_echo_origin_when_credentials_enabled() throws Exception {
        mockMvc.perform(options("/api/test")
                        .header("Origin", "http://example.com")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    public void cors_preflight_should_respect_max_age() throws Exception {
        mockMvc.perform(options("/api/test")
                        .header("Origin", "http://example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    public void actual_request_without_origin_should_succeed_and_not_contain_cors_headers() throws Exception {
        mockMvc.perform(get("/api/test"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    public void actual_request_with_origin_should_echo_origin_and_allow_credentials() throws Exception {
        mockMvc.perform(get("/api/test")
                        .header("Origin", "http://example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://example.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }


    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void objectMapper_should_serialize_local_date_time_correctly() throws Exception {
        LocalDateTime now = LocalDateTime.of(2025, 12, 7, 12, 0, 0);
        String json = objectMapper.writeValueAsString(now);
        assertEquals("\"2025-12-07T12:00:00\"", json);
    }

    @Test
    public void objectMapper_should_deserialize_local_date_time_correctly() throws Exception {
        String json = "\"2025-12-07T12:00:00\"";
        LocalDateTime expected = LocalDateTime.of(2025, 12, 7, 12, 0, 0);
        LocalDateTime parsed = objectMapper.readValue(json, LocalDateTime.class);
        assertEquals(expected, parsed);
    }

    @Test
    public void objectMapper_should_throw_exception_for_invalid_date_time_format() {
        String invalidJson = "\"2025-12-07 12:00:00\"";
        assertThrows(Exception.class, () ->
            objectMapper.readValue(invalidJson, LocalDateTime.class)
        );
    }
}
