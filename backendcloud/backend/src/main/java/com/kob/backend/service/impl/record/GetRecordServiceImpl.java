package com.kob.backend.service.impl.record;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.mapper.RecordMapper;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.Record;
import com.kob.backend.pojo.User;
import com.kob.backend.service.record.GetRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GetRecordServiceImpl implements GetRecordService {
    @Autowired
    private RecordMapper recordMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public JSONObject getRecord(Integer recordId) {
        Record record = recordMapper.selectById(recordId);
        JSONObject resp = new JSONObject();
        if (record == null) {
            resp.put("error_message", "录像不存在");
            return resp;
        }
        User userA = userMapper.selectById(record.getAId());
        User userB = userMapper.selectById(record.getBId());
        JSONObject item = new JSONObject();
        item.put("a_photo", userA != null ? userA.getPhoto() : "");
        item.put("a_username", userA != null ? userA.getUsername() : "");
        item.put("b_photo", userB != null ? userB.getPhoto() : "");
        item.put("b_username", userB != null ? userB.getUsername() : "");
        String result = "平局";
        if ("A".equals(record.getLoser())) result = "B胜";
        else if ("B".equals(record.getLoser())) result = "A胜";
        item.put("result", result);
        item.put("record", record);
        resp.put("error_message", "success");
        resp.put("record_item", item);
        return resp;
    }
}
