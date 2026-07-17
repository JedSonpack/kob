package com.kob.backend.agent.workflow;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BestVersionSelectorTest {

    private final BestVersionSelector selector = new BestVersionSelector();

    private BestVersionSelector.Candidate cand(long id, int iter, double pub, long p95, double hid) {
        return new BestVersionSelector.Candidate(id, iter, pub, p95, hid);
    }

    @Test
    void degradesToV1WhenNewVersionWorse() {
        List<BestVersionSelector.Candidate> cs = Arrays.asList(
                cand(2, 2, 0.6, 10, 0.4), cand(1, 1, 0.5, 10, 0.5));
        // V2 hidden 0.4 < V1 0.5 * 0.95 = 0.475 -> 保留 V1
        assertEquals(1L, selector.select(cs));
    }

    @Test
    void selectsHighestHiddenWhenNoDegrade() {
        List<BestVersionSelector.Candidate> cs = Arrays.asList(
                cand(1, 1, 0.5, 10, 0.5), cand(2, 2, 0.6, 10, 0.6));
        assertEquals(2L, selector.select(cs));
    }

    @Test
    void tieBreaksByPublicThenP95ThenVersionId() {
        // 同 hidden：public 降序
        List<BestVersionSelector.Candidate> cs = Arrays.asList(
                cand(1, 1, 0.6, 10, 0.5), cand(2, 2, 0.5, 10, 0.5));
        assertEquals(1L, selector.select(cs));
        // 同 public：P95 升序
        List<BestVersionSelector.Candidate> cs2 = Arrays.asList(
                cand(1, 1, 0.5, 20, 0.5), cand(2, 2, 0.5, 10, 0.5));
        assertEquals(2L, selector.select(cs2));
        // 同 P95：versionId 升序
        List<BestVersionSelector.Candidate> cs3 = Arrays.asList(
                cand(2, 2, 0.5, 10, 0.5), cand(1, 1, 0.5, 10, 0.5));
        assertEquals(1L, selector.select(cs3));
    }

    @Test
    void returnsNullForEmpty() {
        assertEquals(null, selector.select(java.util.Collections.emptyList()));
    }
}
