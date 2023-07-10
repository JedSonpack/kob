package com.kob.backend.controller.user.account;

import com.kob.backend.service.user.account.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class LoginController {
    @Autowired
    private LoginService loginService;

    @PostMapping("/api/user/account/token/")
    public Map<String,String> getToken(@RequestParam Map<String,String> map){
        String usernmae=map.get("username");
        String passowrd=map.get("password");

        //返回信息
        return loginService.getToken(usernmae,passowrd);
    }

}
