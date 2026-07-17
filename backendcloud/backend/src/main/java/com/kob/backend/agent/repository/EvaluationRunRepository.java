package com.kob.backend.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kob.backend.agent.mapper.EvaluationRunMapper;
import com.kob.backend.agent.model.EvaluationRun;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 评测记录仓库：依赖唯一键幂等保存，重复时复用现有记录。
 */
@Repository
public class EvaluationRunRepository {

    private final EvaluationRunMapper mapper;

    public EvaluationRunRepository(EvaluationRunMapper mapper) {
        this.mapper = mapper;
    }

    public void saveIfAbsent(EvaluationRun run) {
        QueryWrapper<EvaluationRun> w = new QueryWrapper<>();
        w.eq("version_id", run.getVersionId()).eq("dataset_type", run.getDatasetType())
                .eq("opponent_key", run.getOpponentKey())
                .eq("map_seed", run.getMapSeed()).eq("side", run.getSide());
        EvaluationRun existing = mapper.selectOne(w);
        if (existing != null) {
            return;
        }
        run.setCreatedAt(new Date());
        try {
            mapper.insert(run);
        } catch (DuplicateKeyException ignored) {
            // 并发插入：依赖唯一键，忽略
        }
    }

    public List<EvaluationRun> findByVersionAndDataset(Long versionId, String datasetType) {
        QueryWrapper<EvaluationRun> w = new QueryWrapper<>();
        w.eq("version_id", versionId).eq("dataset_type", datasetType);
        return mapper.selectList(w);
    }

    public EvaluationRun findById(Long id) {
        return mapper.selectById(id);
    }
}
