package com.kob.backend.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kob.backend.agent.model.AgentTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {
}
