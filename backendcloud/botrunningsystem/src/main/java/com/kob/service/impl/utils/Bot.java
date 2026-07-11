package com.kob.service.impl.utils;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Bot {
    Integer userId;
    String botCode;
    String input;
    String gameId;  // 审计 2.1：关联对局
    Integer roundId;  // 审计 2.1：关联回合
}

