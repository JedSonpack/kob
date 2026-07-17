package com.kob.backend.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kob.backend.agent.model.BotVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BotVersionMapper extends BaseMapper<BotVersion> {
}
