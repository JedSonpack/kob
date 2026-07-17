package com.kob.service.evaluation;

import com.kob.game.core.FailureReason;
import com.kob.game.core.GameConfig;
import com.kob.game.core.GameEngine;
import com.kob.game.core.GameResult;
import com.kob.game.core.MatchOutcome;
import com.kob.game.core.ReplayFrame;
import com.kob.game.core.Strategy;
import com.kob.game.core.StrategyExecutionException;
import com.kob.game.core.strategy.GreedyBot;
import com.kob.game.core.strategy.SafeBot;
import com.kob.game.core.strategy.TerritoryBot;
import com.kob.service.evaluation.dto.EvaluationMatchResult;
import com.kob.service.evaluation.dto.EvaluationMode;
import com.kob.service.evaluation.dto.EvaluationRequest;
import com.kob.service.evaluation.dto.EvaluationResponse;
import com.kob.service.evaluation.dto.EvaluationSummary;
import com.kob.service.evaluation.protocol.SnapshotInputCodec;
import com.kob.service.evaluation.sandbox.PersistentBotProcess;
import com.kob.service.evaluation.sandbox.PersistentBotProcessFactory;
import com.kob.service.evaluation.sandbox.SandboxErrorCode;
import com.kob.service.evaluation.sandbox.SandboxExecutionException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可信批量评测协调器（阶段 2 任务 4）。
 *
 * <p>在可信主 JVM 中持有 game-core、地图种子、基准策略、胜负计算与指标聚合。
 * 候选 Bot 在独立持久子进程中只编译一次，连续响应多个局面。
 * 公开集 8 种子 × 3 基准 × 2 出生侧 = 48 局；隐藏集 4 × 3 × 2 = 24 局。
 */
public class EvaluationCoordinator {

    private static final int ROWS = 13;
    private static final int COLS = 14;
    private static final int INNER_WALLS = 20;

    private final PersistentBotProcessFactory factory;
    private final long[] publicSeeds;
    private final long[] hiddenSeeds;
    private final int maxRounds;
    private final long batchTimeoutMs;
    private final long hiddenTimeoutMs;
    private final long replayLimitBytes;
    private final EvaluationJobRegistry registry;

    private final GameEngine engine = new com.kob.game.core.DefaultGameEngine();
    private final SnapshotInputCodec codec = new SnapshotInputCodec();
    private final Strategy[] baselines = {new SafeBot(), new GreedyBot(), new TerritoryBot()};
    private final String[] baselineKeys = {"safe", "greedy", "territory"};

    public EvaluationCoordinator(PersistentBotProcessFactory factory,
                                 long[] publicSeeds, long[] hiddenSeeds,
                                 int maxRounds, long batchTimeoutMs, long hiddenTimeoutMs,
                                 long replayLimitBytes) {
        this(factory, publicSeeds, hiddenSeeds, maxRounds, batchTimeoutMs, hiddenTimeoutMs, replayLimitBytes, null);
    }

    /** 带 registry 的构造器：注册运行中进程以支持取消与防重（可为 null）。 */
    public EvaluationCoordinator(PersistentBotProcessFactory factory,
                                 long[] publicSeeds, long[] hiddenSeeds,
                                 int maxRounds, long batchTimeoutMs, long hiddenTimeoutMs,
                                 long replayLimitBytes, EvaluationJobRegistry registry) {
        this.factory = factory;
        this.publicSeeds = publicSeeds;
        this.hiddenSeeds = hiddenSeeds;
        this.maxRounds = maxRounds;
        this.batchTimeoutMs = batchTimeoutMs;
        this.hiddenTimeoutMs = hiddenTimeoutMs;
        this.replayLimitBytes = replayLimitBytes;
        this.registry = registry;
    }

    public EvaluationResponse evaluate(EvaluationRequest request) {
        String requestId = request.getRequestId();
        EvaluationMode mode = request.getMode();
        long[] seeds = seedsFor(mode);

        PersistentBotProcess process = factory.create();
        try {
            try {
                process.start(request.getSourceCode());
            } catch (SandboxExecutionException e) {
                if (e.getCode() == SandboxErrorCode.COMPILE_FAILED) {
                    return new EvaluationResponse(requestId, false, e.getMessage(), null, Collections.<EvaluationMatchResult>emptyList());
                }
                throw e;
            }
            if (registry != null) {
                registry.register(requestId, process);
            }
            if (mode == EvaluationMode.COMPILE_ONLY) {
                return new EvaluationResponse(requestId, true, null, null, Collections.<EvaluationMatchResult>emptyList());
            }

            Strategy candidate = buildCandidateStrategy(process);
            long deadline = System.currentTimeMillis() + (mode == EvaluationMode.HIDDEN ? hiddenTimeoutMs : batchTimeoutMs);

            List<EvaluationMatchResult> matches = new ArrayList<>();
            List<Long> allCandidateNanos = new ArrayList<>();
            for (long seed : seeds) {
                for (int b = 0; b < baselines.length; b++) {
                    for (String side : new String[]{"A", "B"}) {
                        if (System.currentTimeMillis() > deadline) {
                            return aggregate(requestId, matches, allCandidateNanos);
                        }
                        GameConfig config = new GameConfig(ROWS, COLS, INNER_WALLS, seed, maxRounds);
                        GameResult result = "A".equals(side)
                                ? engine.play(config, candidate, baselines[b])
                                : engine.play(config, baselines[b], candidate);
                        matches.add(toMatchResult(seed, baselineKeys[b], side, result));
                        allCandidateNanos.addAll(candidateNanos(result, side));
                    }
                }
            }
            return aggregate(requestId, matches, allCandidateNanos);
        } finally {
            if (registry != null) {
                registry.removeRunning(requestId);
            }
            closeQuietly(process);
        }
    }

    private long[] seedsFor(EvaluationMode mode) {
        if (mode == EvaluationMode.PUBLIC) return publicSeeds;
        if (mode == EvaluationMode.HIDDEN) return hiddenSeeds;
        return new long[0];
    }

    private Strategy buildCandidateStrategy(final PersistentBotProcess process) {
        return new Strategy() {
            @Override
            public int nextMove(com.kob.game.core.GameSnapshot snapshot) {
                try {
                    return process.decide(codec.encode(snapshot)).getDirection();
                } catch (SandboxExecutionException error) {
                    if (error.getCode() == SandboxErrorCode.STEP_TIMEOUT) {
                        throw new StrategyExecutionException(FailureReason.STEP_TIMEOUT, error.getMessage());
                    }
                    if (error.getCode() == SandboxErrorCode.OUTPUT_LIMIT) {
                        throw new StrategyExecutionException(FailureReason.OUTPUT_LIMIT, error.getMessage());
                    }
                    throw new StrategyExecutionException(FailureReason.SANDBOX_VIOLATION, error.getMessage());
                }
            }
        };
    }

    private EvaluationMatchResult toMatchResult(long seed, String opponentKey, String side, GameResult result) {
        boolean candidateIsA = "A".equals(side);
        List<Long> nanos = candidateIsA ? result.getDecisionNanosA() : result.getDecisionNanosB();
        int invalid = candidateIsA ? result.getInvalidMoveCountA() : result.getInvalidMoveCountB();
        FailureReason failure = candidateIsA ? result.getFailureReasonA() : result.getFailureReasonB();
        String resultStr = candidateResult(candidateIsA, result.getOutcome());
        return new EvaluationMatchResult(
                opponentKey,
                seed,
                side,
                resultStr,
                result.getRounds(),
                p95Ms(nanos),
                invalid,
                failure == null || failure == FailureReason.NONE ? null : failure.name(),
                encodeReplay(result.getReplay())
        );
    }

    private String candidateResult(boolean candidateIsA, MatchOutcome outcome) {
        if (outcome == MatchOutcome.DRAW || outcome == MatchOutcome.TIMEOUT) return "DRAW";
        boolean candidateWon = (candidateIsA && outcome == MatchOutcome.A_WIN)
                || (!candidateIsA && outcome == MatchOutcome.B_WIN);
        return candidateWon ? "WIN" : "LOSS";
    }

    private List<Long> candidateNanos(GameResult result, String side) {
        return "A".equals(side) ? result.getDecisionNanosA() : result.getDecisionNanosB();
    }

    private EvaluationResponse aggregate(String requestId, List<EvaluationMatchResult> matches,
                                         List<Long> allCandidateNanos) {
        int gameCount = matches.size();
        int wins = 0, draws = 0, losses = 0;
        long totalRounds = 0;
        int invalidTotal = 0;
        Map<String, Integer> failureCounts = new LinkedHashMap<>();
        for (EvaluationMatchResult m : matches) {
            if ("WIN".equals(m.getResult())) wins++;
            else if ("DRAW".equals(m.getResult())) draws++;
            else losses++;
            totalRounds += m.getRounds();
            invalidTotal += m.getInvalidMoveCount();
            if (m.getFailureReason() != null) {
                failureCounts.merge(m.getFailureReason(), 1, Integer::sum);
            }
        }
        double score = gameCount == 0 ? 0.0 : (wins + 0.5 * draws) / gameCount;
        double winRate = gameCount == 0 ? 0.0 : (double) wins / gameCount;
        double avgRounds = gameCount == 0 ? 0.0 : (double) totalRounds / gameCount;
        EvaluationSummary summary = new EvaluationSummary(
                gameCount, score, winRate, avgRounds, p95Ms(allCandidateNanos), invalidTotal, failureCounts);
        return new EvaluationResponse(requestId, true, null, summary, matches);
    }

    private String encodeReplay(List<ReplayFrame> frames) {
        StringBuilder sb = new StringBuilder();
        for (ReplayFrame f : frames) {
            if (sb.length() > 0) sb.append(';');
            sb.append(f.getDirectionA()).append(',').append(f.getDirectionB());
        }
        String encoded = sb.toString();
        if (encoded.getBytes(StandardCharsets.UTF_8).length > replayLimitBytes) {
            return "REPLAY_LIMIT";
        }
        return encoded;
    }

    /** P95（纳秒），使用向上取整索引：index = ceil(n * 0.95) - 1。 */
    static long p95Nanos(List<Long> nanos) {
        if (nanos.isEmpty()) return 0L;
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        if (index < 0) index = 0;
        return sorted.get(index);
    }

    static long p95Ms(List<Long> nanos) {
        return p95Nanos(nanos) / 1_000_000L;
    }

    /** 计分（平局 0.5），用于聚合测试。 */
    static double score(int wins, int draws, int gameCount) {
        return gameCount == 0 ? 0.0 : (wins + 0.5 * draws) / gameCount;
    }

    private static void closeQuietly(PersistentBotProcess process) {
        if (process == null) return;
        try {
            process.close();
        } catch (RuntimeException ignored) {
        }
    }
}
