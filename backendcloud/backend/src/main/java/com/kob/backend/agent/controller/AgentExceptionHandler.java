package com.kob.backend.agent.controller;

import com.kob.backend.agent.service.impl.AgentTaskConflictException;
import com.kob.backend.agent.service.impl.AgentTaskException;
import com.kob.backend.agent.service.impl.AgentTaskNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent API 异常映射：冲突 409、不存在 404、其他业务异常 400。
 */
@RestControllerAdvice
public class AgentExceptionHandler {

    @ExceptionHandler(AgentTaskConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(AgentTaskConflictException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AgentTaskNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(AgentTaskNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AgentTaskException.class)
    public ResponseEntity<Map<String, String>> badRequest(AgentTaskException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("error_message", message);
        return ResponseEntity.status(status).body(body);
    }
}
