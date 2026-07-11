package com.kob.service.impl.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
public class Consumer extends Thread {
    private Bot bot;
    private static RestTemplate restTemplate;
    private static String receiveBotMoveUrl;  // 审计 4.1：外置到配置
    private static BotExecutor botExecutor;  // 审计 2.2：执行器边界

    @Value("${kob.service.backend.receive-bot-move-url:http://127.0.0.1:3000/pk/receive/bot/move/}")
    public void setReceiveBotMoveUrl(String receiveBotMoveUrl) { Consumer.receiveBotMoveUrl = receiveBotMoveUrl; }

    @Autowired
    public void setRestTemplate(RestTemplate restTemplate) {
        Consumer.restTemplate = restTemplate;
    }

    @Autowired
    public void setBotExecutor(BotExecutor botExecutor) {
        Consumer.botExecutor = botExecutor;
    }

    public void startTimeout(long timeout, Bot bot) {
        this.bot = bot;
        this.start();

        try {
            this.join(timeout);  // 最多等待timeout秒
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            this.interrupt();  // 终端当前线程
        }
    }

    @Override
    public void run() {
        Integer direction;
        try {
            direction = botExecutor.execute(bot.getBotCode(), bot.getInput());  // 审计 2.2：委托执行器
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        System.out.println("move-direction: " + bot.getUserId() + " " + direction);

        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("user_id", bot.getUserId().toString());
        data.add("direction", direction.toString());
        data.add("game_id", bot.getGameId());  // 审计 2.1：回传对局与回合
        data.add("round_id", bot.getRoundId().toString());

        restTemplate.postForObject(receiveBotMoveUrl, data, String.class);

    }
}
