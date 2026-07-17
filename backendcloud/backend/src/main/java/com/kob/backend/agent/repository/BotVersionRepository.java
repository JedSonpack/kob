package com.kob.backend.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kob.backend.agent.mapper.BotVersionMapper;
import com.kob.backend.agent.mapper.EvaluationRunMapper;
import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.model.EvaluationRun;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Bot 版本仓库：保存前校验迭代/尝试/父版本不变量。
 *
 * <ul>
 *   <li>iteration 必须在 1..3，attempt 必须在 1..2。</li>
 *   <li>attempt=2 时同轮必须有 attempt 1。</li>
 *   <li>iteration&gt;1 时父版本必须已完成公开评测。</li>
 * </ul>
 */
@Repository
public class BotVersionRepository {

    private final BotVersionMapper mapper;
    private final EvaluationRunMapper evaluationRunMapper;

    public BotVersionRepository(BotVersionMapper mapper, EvaluationRunMapper evaluationRunMapper) {
        this.mapper = mapper;
        this.evaluationRunMapper = evaluationRunMapper;
    }

    public BotVersion save(BotVersion version) {
        int iter = version.getIteration();
        int attempt = version.getAttempt();
        if (iter < 1 || iter > 3) {
            throw new IllegalArgumentException("iteration 不在 1..3: " + iter);
        }
        if (attempt < 1 || attempt > 2) {
            throw new IllegalArgumentException("attempt 不在 1..2: " + attempt);
        }
        if (attempt == 2) {
            QueryWrapper<BotVersion> w = new QueryWrapper<>();
            w.eq("task_id", version.getTaskId()).eq("iteration", iter).eq("attempt", 1);
            Long count = mapper.selectCount(w);
            if (count == null || count == 0) {
                throw new IllegalStateException("attempt 2 缺少 attempt 1");
            }
        }
        if (iter > 1) {
            if (version.getParentVersionId() == null) {
                throw new IllegalStateException("iteration>1 缺少父版本");
            }
            QueryWrapper<EvaluationRun> w = new QueryWrapper<>();
            w.eq("version_id", version.getParentVersionId()).eq("dataset_type", "PUBLIC");
            Long count = evaluationRunMapper.selectCount(w);
            if (count == null || count == 0) {
                throw new IllegalStateException("父版本未完成公开评测");
            }
        }
        mapper.insert(version);
        return version;
    }

    public BotVersion findById(Long id) {
        return mapper.selectById(id);
    }

    public List<BotVersion> findByTask(Long taskId) {
        QueryWrapper<BotVersion> w = new QueryWrapper<>();
        w.eq("task_id", taskId).orderByAsc("iteration", "attempt");
        return mapper.selectList(w);
    }

    public boolean updateCompileStatus(Long versionId, String compileStatus, String compileError) {
        BotVersion v = mapper.selectById(versionId);
        if (v == null) return false;
        v.setCompileStatus(compileStatus);
        v.setCompileError(compileError);
        return mapper.updateById(v) > 0;
    }

    public boolean markAccepted(Long versionId, boolean accepted) {
        BotVersion v = mapper.selectById(versionId);
        if (v == null) return false;
        v.setAccepted(accepted ? 1 : 0);
        return mapper.updateById(v) > 0;
    }
}
