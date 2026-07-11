package com.kob.service.impl.utils;

import org.joor.Reflect;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bot 沙箱执行子进程入口（审计任务 2.3）。
 *
 * <p>在独立 JVM 进程中编译并执行用户 Bot 代码，与主服务进程隔离（无法访问其内存/Bean/Mapper）。
 * 安装 SecurityManager 禁止网络、文件写/删除、exec；放行读、stdout、类加载等。
 * 从 stdin 读取 Bot 源码，编译执行后将方向打印到 stdout。
 *
 * <p>需在 JDK 8 运行（jOOR-java-8 兼容）；由 ProcessSandboxBotExecutor 用配置的 JAVA_HOME 启动。
 */
public class SandboxMain {

    public static void main(String[] args) throws Exception {
        // 审计 2.3：SecurityManager 限制网络/写/exec
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkPermission(java.security.Permission perm) {
                if (perm instanceof java.net.SocketPermission) {
                    throw new SecurityException("网络访问被禁止");
                }
                if (perm instanceof java.io.FilePermission) {
                    String actions = perm.getActions();
                    if (actions.contains("write") || actions.contains("delete")) {
                        throw new SecurityException("文件写/删除被禁止");
                    }
                }
                if (perm instanceof java.lang.RuntimePermission && "exec".equals(perm.getName())) {
                    throw new SecurityException("执行外部命令被禁止");
                }
                // 读、stdout(FileDescriptor 写)、类加载、exit 等放行
            }

            @Override
            public void checkPermission(java.security.Permission perm, Object context) {
                checkPermission(perm);
            }
        });

        // 从 stdin 读取 Bot 源码（全部字节）
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try (InputStream is = System.in) {
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
        }
        String botCode = new String(bos.toByteArray());

        // 编译并执行（沿用 jOOR）
        String uid = UUID.randomUUID().toString().substring(0, 8);
        int k = botCode.indexOf(" implements java.util.function.Supplier<Integer>");
        String code = botCode.substring(0, k) + uid + botCode.substring(k);
        Supplier<Integer> bot = Reflect.compile("com.kob.test.Bot" + uid, code).create().get();
        Integer direction = bot.get();

        System.out.println(direction);
    }
}
