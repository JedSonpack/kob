package com.kob.backend.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发往 botsys 评测接口的请求 DTO（字段名与 botsys EvaluationRequest 一致）。
 * mode 为 COMPILE_ONLY/PUBLIC/HIDDEN 的字符串。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationRequest {
    private String requestId;
    private String sourceCode;
    private String mode;
}
