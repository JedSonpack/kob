package com.kob.backend.service.impl.user.account;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.User;
import com.kob.backend.service.user.account.RegisterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RegisterServiceImpl implements RegisterService {

    @Autowired
    private UserMapper userMapper;

    //加密
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public Map<String, String> register(String username, String password, String confirmedPassword) {

        Map<String, String> res = new HashMap<>();

        if (username == null) {
            res.put("error_message", "用户名不能为空");
            return res;
        }
        if (password == null || confirmedPassword == null) {
            res.put("error_message", "密码不能为空");
        }

        username = username.trim();

        if (username.length() == 0) {
            res.put("error_message", "用户名不能为空");
            return res;
        }

        if(password.length()==0||confirmedPassword.length()==0){
            res.put("error_message","密码不能为空");
            return res;
        }

        if (username.length() > 100) {
            res.put("error_message", "用户名长度不能大于100");
            return res;
        }

        if (password.length() > 100 || confirmedPassword.length() > 100) {
            res.put("error_message", "密码长度不能大于100");
            return res;
        }

        if (!password.equals(confirmedPassword)) {
            res.put("error_message", "两次输入的密码不一致");
            return res;
        }

        // 查找与用户名相同的用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        List<User> users = userMapper.selectList(queryWrapper);
        if (!users.isEmpty()) {
            res.put("error_message", "用户名已存在");
            return res;
        }

        String encodedPassword = passwordEncoder.encode(password);
        String photo = "https://www.qqkw.com/d/file/p/2023/02-25/d803531ec40ba52142e0015c1c0233fe.jpg";
        User user = new User(null, username, encodedPassword, photo);
        userMapper.insert(user);

        res.put("error_message", "success");
        return res;
    }
}

