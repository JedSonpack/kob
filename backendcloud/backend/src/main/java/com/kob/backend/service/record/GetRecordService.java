package com.kob.backend.service.record;

import com.alibaba.fastjson.JSONObject;

/**
 * 按录像 ID 获取单条对局记录（审计任务 3.2）。
 * 支持录像详情页直接访问/刷新时按 URL 中的 recordId 拉取。
 */
public interface GetRecordService {
    JSONObject getRecord(Integer recordId);
}
