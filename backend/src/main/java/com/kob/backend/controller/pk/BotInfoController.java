package com.kob.backend.controller.pk;

import com.sun.org.apache.xerces.internal.xs.StringList;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedList;
import java.util.List;

@RestController
@RequestMapping("/pk/")
public class BotInfoController {

    @RequestMapping("getbotinfo/")
    public List<String> getBotInfo() {
        List list = new LinkedList();
        list.add("tiger");
        list.add("apple");
        list.add("sword");
        return list;
    }
}
