package com.kob.backend.controller.pk;

import com.sun.org.apache.xerces.internal.xs.StringList;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pk/")
public class BotInfoController {

    @RequestMapping("getbotinfo/")
    public Map<String,String> getBotInfo() {
        Map<String, String> m = new HashMap<>();
        m.put("name","AcBot" );
        m.put("rating","1500" );
        return m;
    }
}
