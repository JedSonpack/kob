package com.kob.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

//测试加密模块

@SpringBootTest
class BackendApplicationTests {

    @Test
    void contextLoads() {
    PasswordEncoder passwordEncoder=new BCryptPasswordEncoder();
    System.out.println(passwordEncoder.encode("bbb"));
    System.out.println(passwordEncoder.matches("bbb","$2a$10$xn7fcnS2DXz4/Gac.WigVe2uzdsWH16P6OgRRyO8aMu5eDfBrKrQu"));
    }

}
