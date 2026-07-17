package com.kob.service.evaluation;

import com.kob.service.evaluation.sandbox.PersistentBotProcessFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配 {@link EvaluationCoordinator}，从配置读取种子与超时，注入 registry 以支持取消与防重。
 */
@Configuration
public class EvaluationConfig {

    @Bean
    public EvaluationCoordinator evaluationCoordinator(
            PersistentBotProcessFactory factory,
            EvaluationJobRegistry registry,
            @Value("${kob.bot.evaluation.public-seeds}") long[] publicSeeds,
            @Value("${kob.bot.evaluation.hidden-seeds}") long[] hiddenSeeds,
            @Value("${kob.bot.evaluation.max-rounds:1000}") int maxRounds,
            @Value("${kob.bot.evaluation.batch-timeout-ms:120000}") long batchTimeoutMs,
            @Value("${kob.bot.evaluation.hidden-timeout-ms:60000}") long hiddenTimeoutMs,
            @Value("${kob.bot.evaluation.replay-limit-bytes:1048576}") long replayLimitBytes) {
        return new EvaluationCoordinator(factory, publicSeeds, hiddenSeeds,
                maxRounds, batchTimeoutMs, hiddenTimeoutMs, replayLimitBytes, registry);
    }
}
