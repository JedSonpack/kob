package com.kob.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RestTemplateConfig 测试（审计任务 4.1）。
 * 验证 RestTemplate 配置了连接/读取超时（默认值与自定义覆盖），避免下游故障无限挂起（审计 6.2）。
 * SimpleClientHttpRequestFactory 无公开 getter，故用反射读取私有字段。
 */
class RestTemplateConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RestTemplateConfig.class);

    private static int timeoutField(Object factory, String fieldName) throws Exception {
        Field f = SimpleClientHttpRequestFactory.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.getInt(factory);
    }

    @Test
    void restTemplate_hasDefaultTimeouts() {
        runner.run(context -> {
            SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory)
                    context.getBean(RestTemplate.class).getRequestFactory();
            assertEquals(5000, timeoutField(factory, "connectTimeout"));
            assertEquals(3000, timeoutField(factory, "readTimeout"));
        });
    }

    @Test
    void restTemplate_respectsCustomTimeouts() {
        runner.withPropertyValues(
                "kob.http.connect-timeout-ms=1000",
                "kob.http.read-timeout-ms=500")
                .run(context -> {
                    SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory)
                            context.getBean(RestTemplate.class).getRequestFactory();
                    assertEquals(1000, timeoutField(factory, "connectTimeout"));
                    assertEquals(500, timeoutField(factory, "readTimeout"));
                });
    }
}
