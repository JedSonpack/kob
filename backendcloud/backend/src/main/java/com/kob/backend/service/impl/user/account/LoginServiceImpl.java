package com.kob.backend.service.impl.user.account;

import com.kob.backend.pojo.User;
import com.kob.backend.service.impl.utils.UserDetailsImpl;
import com.kob.backend.service.user.account.LoginService;
import com.kob.backend.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Override
    public Map<String, String> getToken(String username, String password) {

        //封装用户名和密码(加密后)
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
        //验证是否正常登录
        Authentication authenticate = authenticationManager.authenticate(authenticationToken); //登陆失败，自动处理，例如报异常

        //取出用户
        UserDetailsImpl loginuser = (UserDetailsImpl) authenticate.getPrincipal();

        //得到用户
        User user = loginuser.getUser();

        //创键jwt-token
        String jwt = JwtUtil.createJWT(user.getId().toString());

        Map<String, String> map = new HashMap<>();

        map.put("error_message", "success");
        map.put("token", jwt);

        return map;
    }
}
