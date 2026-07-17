package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.AgentAction;
import com.kob.backend.agent.model.AgentTaskStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 假 LLM 客户端：决策完全由状态与迭代决定，不使用随机数；生成可编译的 com.kob.test.Bot。
 *
 * <p>GENERATING -> GENERATE_CODE；REPAIRING -> REPAIR_CODE；
 * ANALYZING 且未达上限 -> IMPROVE_CODE；ANALYZING 且达上限 -> FINISH。
 */
@Component
@ConditionalOnProperty(name = "kob.agent.llm.provider", havingValue = "fake", matchIfMissing = true)
public class FakeLlmClient implements LlmClient {

    @Override
    public LlmDecision decide(LlmContext context) {
        AgentTaskStatus status = context.getStatus();
        int iteration = context.getIteration();
        int max = context.getMaxIterations();
        if (status == AgentTaskStatus.GENERATING) {
            return new LlmDecision(AgentAction.GENERATE_CODE, "V1: 取第一个安全方向",
                    "初始生成", source(1), 100, 50);
        }
        if (status == AgentTaskStatus.REPAIRING) {
            return new LlmDecision(AgentAction.REPAIR_CODE, "V1(修复): 取第一个安全方向",
                    "修复编译错误", source(1), 100, 50);
        }
        if (status == AgentTaskStatus.ANALYZING) {
            if (iteration < max) {
                int next = iteration + 1;
                return new LlmDecision(AgentAction.IMPROVE_CODE, "V" + next + ": 扩大可达空间",
                        "基于公开集失败改进", source(next), 100, 50);
            }
            return new LlmDecision(AgentAction.FINISH, "公开集已稳定",
                    "达到迭代上限", null, 100, 50);
        }
        throw new IllegalStateException("FakeLlmClient 不支持状态: " + status);
    }

    private static String source(int version) {
        int dir = (version - 1) % 4;
        return "package com.kob.test;\n" +
                "public class Bot {\n" +
                "  public Integer nextMove(String input) { return " + dir + "; }\n" +
                "}";
    }
}
