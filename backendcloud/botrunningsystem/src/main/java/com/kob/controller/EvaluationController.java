package com.kob.controller;

import com.kob.service.evaluation.EvaluationConflictException;
import com.kob.service.evaluation.EvaluationService;
import com.kob.service.evaluation.dto.EvaluationRequest;
import com.kob.service.evaluation.dto.EvaluationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 评测内部 HTTP API（阶段 2 任务 5），仅本地 127.0.0.1 放行（见 SecurityConfig）。
 *
 * <ul>
 *   <li>POST /bot/evaluate/：评测一个版本；缺失字段返回 400，重复执行返回 409。</li>
 *   <li>POST /bot/evaluate/{requestId}/cancel/：取消运行中评测。</li>
 * </ul>
 */
@RestController
public class EvaluationController {

    private final EvaluationService evaluationService;

    @Autowired
    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/bot/evaluate/")
    public ResponseEntity<Object> evaluate(@RequestBody EvaluationRequest request) {
        if (request == null
                || isBlank(request.getRequestId())
                || isBlank(request.getSourceCode())
                || request.getMode() == null) {
            return ResponseEntity.badRequest().body(errorBody("requestId、sourceCode、mode 不能为空"));
        }
        try {
            EvaluationResponse response = evaluationService.evaluate(request);
            return ResponseEntity.ok((Object) response);
        } catch (EvaluationConflictException e) {
            return ResponseEntity.status(409).body(errorBody("评测任务正在执行: " + e.getRequestId()));
        }
    }

    @PostMapping("/bot/evaluate/{requestId}/cancel/")
    public Map<String, Object> cancel(@PathVariable String requestId) {
        boolean cancelled = evaluationService.cancel(requestId);
        Map<String, Object> result = new HashMap<>();
        result.put("requestId", requestId);
        result.put("cancelled", cancelled);
        return result;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static Map<String, String> errorBody(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("error", message);
        return body;
    }
}
