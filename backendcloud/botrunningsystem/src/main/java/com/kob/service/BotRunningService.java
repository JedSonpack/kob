package com.kob.service;

public interface BotRunningService {

    String addBot(Integer userId, String botCode, String input, String gameId, Integer roundId);
}
