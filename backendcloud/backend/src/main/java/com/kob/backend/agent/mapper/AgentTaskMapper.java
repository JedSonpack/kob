package com.kob.backend.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kob.backend.agent.model.AgentTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Agent 任务 Mapper。{@link #compareAndSetStatus} 以 id+version+expectedStatus 做乐观锁 CAS。
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    @Update({
            "<script>",
            "UPDATE agent_task",
            "SET status = #{targetStatus}, version = version + 1, updated_at = NOW()",
            "<if test='currentIteration != null'>, current_iteration = #{currentIteration}</if>",
            "<if test='bestVersionId != null'>, best_version_id = #{bestVersionId}</if>",
            "<if test='errorCode != null'>, error_code = #{errorCode}</if>",
            "<if test='errorMessage != null'>, error_message = #{errorMessage}</if>",
            "<if test='clearActiveSlot'>, active_slot = NULL</if>",
            "WHERE id = #{id} AND version = #{version} AND status = #{expectedStatus}",
            "</script>"
    })
    int compareAndSetStatus(@Param("id") Long id,
                            @Param("version") Integer version,
                            @Param("expectedStatus") String expectedStatus,
                            @Param("targetStatus") String targetStatus,
                            @Param("currentIteration") Integer currentIteration,
                            @Param("bestVersionId") Long bestVersionId,
                            @Param("errorCode") String errorCode,
                            @Param("errorMessage") String errorMessage,
                            @Param("clearActiveSlot") boolean clearActiveSlot);
}
