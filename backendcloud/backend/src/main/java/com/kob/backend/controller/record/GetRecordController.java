package com.kob.backend.controller.record;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.service.record.GetRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GetRecordController {
    @Autowired
    private GetRecordService getRecordService;

    @GetMapping("/api/record/get/")
    public JSONObject getRecord(@RequestParam Integer recordId) {
        return getRecordService.getRecord(recordId);
    }
}
