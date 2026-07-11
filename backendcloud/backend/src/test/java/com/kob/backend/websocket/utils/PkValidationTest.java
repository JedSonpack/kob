package com.kob.backend.websocket.utils;

import com.kob.backend.pojo.Bot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PkValidation 单元测试（审计任务 1.2）。
 * 覆盖：非法方向、他人 Bot、非法 Bot ID、合法 Bot 与真人操作。
 */
class PkValidationTest {

    private Bot bot(Integer id, Integer userId) {
        return new Bot(id, userId, "title", "desc", "content", null, null);
    }

    // ---------- 方向校验 ----------

    @Test
    void isValidDirection_acceptsZeroToThree() {
        assertTrue(PkValidation.isValidDirection(0));
        assertTrue(PkValidation.isValidDirection(1));
        assertTrue(PkValidation.isValidDirection(2));
        assertTrue(PkValidation.isValidDirection(3));
    }

    @Test
    void isValidDirection_rejectsOutOfRangeAndNull() {
        assertFalse(PkValidation.isValidDirection(-1));
        assertFalse(PkValidation.isValidDirection(4));
        assertFalse(PkValidation.isValidDirection(null));
    }

    // ---------- Bot 归属校验 ----------

    @Test
    void isBotAllowed_humanPlayAlwaysAllowed() {
        assertTrue(PkValidation.isBotAllowed(-1, null, 1));
    }

    @Test
    void isBotAllowed_ownBotAllowed() {
        assertTrue(PkValidation.isBotAllowed(5, bot(5, 1), 1));
    }

    @Test
    void isBotAllowed_otherUserBotRejected() {
        assertFalse(PkValidation.isBotAllowed(5, bot(5, 2), 1));
    }

    @Test
    void isBotAllowed_nonExistentBotRejected() {
        assertFalse(PkValidation.isBotAllowed(999, null, 1));
    }

    @Test
    void isBotAllowed_invalidBotIdRejected() {
        assertFalse(PkValidation.isBotAllowed(null, null, 1));
        assertFalse(PkValidation.isBotAllowed(-2, null, 1));
    }

    @Test
    void isBotAllowed_nullUserIdRejected() {
        assertFalse(PkValidation.isBotAllowed(5, bot(5, 1), null));
    }

    // ---------- Bot 回调关联（P0-4）----------

    @Test
    void isMoveForCurrentRound_acceptsMatchingGameAndRound() {
        assertTrue(PkValidation.isMoveForCurrentRound("g1", 3, "g1", 3));
    }

    @Test
    void isMoveForCurrentRound_rejectsOtherGame() {
        assertFalse(PkValidation.isMoveForCurrentRound("g1", 3, "g2", 3));
    }

    @Test
    void isMoveForCurrentRound_rejectsOtherRound() {
        assertFalse(PkValidation.isMoveForCurrentRound("g1", 3, "g1", 4));
    }

    @Test
    void isMoveForCurrentRound_rejectsNullCurrent() {
        assertFalse(PkValidation.isMoveForCurrentRound("g1", 3, null, 3));
        assertFalse(PkValidation.isMoveForCurrentRound("g1", 3, "g1", null));
    }
}
