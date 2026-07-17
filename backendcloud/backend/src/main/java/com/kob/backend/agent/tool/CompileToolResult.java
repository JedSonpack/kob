package com.kob.backend.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 编译工具结果。
 */
@Data
@AllArgsConstructor
public class CompileToolResult {
    private final boolean succeeded;
    private final String compileError;
}
