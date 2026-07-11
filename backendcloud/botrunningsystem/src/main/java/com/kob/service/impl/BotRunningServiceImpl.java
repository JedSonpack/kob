package com.kob.service.impl;

import com.kob.service.BotRunningService;
import com.kob.service.impl.utils.BotPool;
import org.springframework.stereotype.Service;

@Service
public class BotRunningServiceImpl implements BotRunningService {

    public final static BotPool botPool = new BotPool();

    @Override
    public String addBot(Integer userId, String botCode, String input, String gameId, Integer roundId) {
        System.out.println("当前用户是" + userId + "输入信息为" + input);
        botPool.addBot(userId, botCode, input, gameId, roundId);
        return "add bot success";
    }
}
