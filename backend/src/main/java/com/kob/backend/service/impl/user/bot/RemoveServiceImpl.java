package com.kob.backend.service.impl.user.bot;

import com.kob.backend.mapper.BotMapper;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.User;
import com.kob.backend.service.impl.utils.UserDetailsImpl;
import com.kob.backend.service.user.bot.RemoveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RemoveServiceImpl implements RemoveService {

    @Autowired
    BotMapper botMapper;


    @Override
    public Map<String, String> remove(Map<String, String> data) {

        //首先获取当前用户的id
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

        UserDetailsImpl userDetails = (UserDetailsImpl) usernamePasswordAuthenticationToken.getPrincipal();

        User user = userDetails.getUser();

        int bot_id = Integer.parseInt(data.get("bot_id"));

        int user_id = user.getId();

        Bot bot = botMapper.selectById(bot_id);

        Map<String, String> map = new HashMap<>();
        if (bot == null) {
            map.put("error_message", "bot不存在或者已被删除");
            return map;
        }

        if (bot.getUserId() != user_id) {
            map.put("error_message", "用户权限不足");
            return map;
        }

        botMapper.deleteById(bot_id);

        map.put("error_message", "success");

        return map;
    }
}
