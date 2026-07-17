package com.kob.backend.agent.workflow;

import com.kob.backend.agent.model.BotVersion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 最佳版本选择器（设计规格 9.4）。
 *
 * <p>排序：隐藏得分降序 -> 公开得分降序 -> P95 升序 -> versionId 升序。
 * 若新版本相对 V1 隐藏得分退化超过 5%，保留 V1。
 */
@Component
public class BestVersionSelector {

    public Long select(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingDouble((Candidate c) -> -c.hiddenScore)
                .thenComparingDouble((Candidate c) -> -c.publicScore)
                .thenComparingLong((Candidate c) -> c.publicP95Ms)
                .thenComparingLong((Candidate c) -> c.versionId));

        Candidate best = sorted.get(0);
        Candidate v1 = null;
        for (Candidate c : sorted) {
            if (c.iteration == 1) {
                v1 = c;
                break;
            }
        }
        if (v1 != null && !best.versionId.equals(v1.versionId)
                && v1.hiddenScore > 0
                && best.hiddenScore < v1.hiddenScore * 0.95) {
            return v1.versionId;
        }
        return best.versionId;
    }

    public static Candidate candidate(BotVersion version, double publicScore, long publicP95Ms,
                                       double hiddenScore) {
        return new Candidate(version.getId(), version.getIteration(), publicScore, publicP95Ms, hiddenScore);
    }

    public static final class Candidate {
        private final Long versionId;
        private final int iteration;
        private final double publicScore;
        private final long publicP95Ms;
        private final double hiddenScore;

        public Candidate(Long versionId, int iteration, double publicScore, long publicP95Ms, double hiddenScore) {
            this.versionId = versionId;
            this.iteration = iteration;
            this.publicScore = publicScore;
            this.publicP95Ms = publicP95Ms;
            this.hiddenScore = hiddenScore;
        }

        public Long getVersionId() { return versionId; }
        public int getIteration() { return iteration; }
        public double getPublicScore() { return publicScore; }
        public long getPublicP95Ms() { return publicP95Ms; }
        public double getHiddenScore() { return hiddenScore; }
    }
}
