package com.kob.backend.agent.controller;

import com.kob.backend.agent.dto.AgentTaskDetailDto;
import com.kob.backend.agent.service.AgentTaskService;
import com.kob.backend.agent.service.impl.AgentTaskConflictException;
import com.kob.backend.agent.service.impl.AgentTaskException;
import com.kob.backend.agent.service.impl.AgentTaskNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentTaskControllerTest {

    private MockMvc mockMvc;
    private AgentTaskService service;

    @BeforeEach
    void setup() {
        service = mock(AgentTaskService.class);
        AgentTaskController controller = new AgentTaskController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "agentTaskService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AgentExceptionHandler())
                .build();
    }

    @Test
    void createTaskReturns200() throws Exception {
        when(service.createTask(any())).thenReturn(1L);
        mockMvc.perform(post("/api/agent/tasks/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"goal\",\"maxIterations\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task_id").value(1));
    }

    @Test
    void createTaskReturns400OnInvalidGoal() throws Exception {
        when(service.createTask(any())).thenThrow(new AgentTaskException("目标不能为空"));
        mockMvc.perform(post("/api/agent/tasks/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"\",\"maxIterations\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTaskDetailReturns404WhenNotFound() throws Exception {
        when(service.getTaskDetail(org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(new AgentTaskNotFoundException("任务不存在"));
        mockMvc.perform(get("/api/agent/tasks/999/"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelTaskReturns409OnConflict() throws Exception {
        org.mockito.Mockito.doThrow(new AgentTaskConflictException("任务已终态"))
                .when(service).cancelTask(org.mockito.ArgumentMatchers.anyLong());
        mockMvc.perform(post("/api/agent/tasks/1/cancel/"))
                .andExpect(status().isConflict());
    }

    @Test
    void getTaskDetailReturns200() throws Exception {
        when(service.getTaskDetail(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new AgentTaskDetailDto());
        mockMvc.perform(get("/api/agent/tasks/1/"))
                .andExpect(status().isOk());
    }
}
