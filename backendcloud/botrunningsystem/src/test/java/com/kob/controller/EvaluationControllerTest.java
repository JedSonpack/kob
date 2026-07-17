package com.kob.controller;

import com.kob.service.evaluation.EvaluationService;
import com.kob.service.evaluation.dto.EvaluationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EvaluationControllerTest {

    private MockMvc mockMvc;
    private EvaluationService service;

    @BeforeEach
    void setup() {
        service = mock(EvaluationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EvaluationController(service)).build();
    }

    @Test
    void evaluateReturns200ForValidRequest() throws Exception {
        EvaluationResponse response = new EvaluationResponse("req-1", true, null, null, Collections.emptyList());
        when(service.evaluate(any())).thenReturn(response);

        mockMvc.perform(post("/bot/evaluate/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-1\",\"sourceCode\":\"x\",\"mode\":\"PUBLIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.compileSucceeded").value(true));
    }

    @Test
    void evaluateReturns400WhenRequestIdMissing() throws Exception {
        mockMvc.perform(post("/bot/evaluate/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCode\":\"x\",\"mode\":\"PUBLIC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evaluateReturns400WhenSourceMissing() throws Exception {
        mockMvc.perform(post("/bot/evaluate/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-1\",\"mode\":\"PUBLIC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelReturns200() throws Exception {
        when(service.cancel(eq("req-1"))).thenReturn(true);

        mockMvc.perform(post("/bot/evaluate/req-1/cancel/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true));
    }
}
