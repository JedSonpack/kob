package com.kob.backend.service.pk;

import java.util.Date;

/**
 * 游戏结果持久化服务（审计任务 2.4）。
 * 原子保存双方积分与战绩，避免中途失败导致积分与记录不一致。
 */
public interface GameResultService {

    /**
     * 赢加 5 分，输扣 2 分，平局不变；并写入对局记录。整段在一个事务内。
     *
     * @param loser "A"/"B"/"all"（all 为平局）
     */
    void saveResult(Integer aId, Integer bId, Integer aSx, Integer aSy,
                    Integer bSx, Integer bSy, String aSteps, String bSteps,
                    String map, String loser, Date createtime);
}
