package com.kob.service.evaluation;

import com.kob.service.evaluation.dto.EvaluationResponse;
import com.kob.service.evaluation.sandbox.PersistentBotProcess;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 评测任务注册表：幂等缓存已完成响应，跟踪运行中进程以支持取消与防重。
 *
 * <p>规则：
 * <ul>
 *   <li>已完成 requestId 直接返回保存的响应。</li>
 *   <li>同一 requestId 正在执行时不启动第二个进程（由调用方判断）。</li>
 *   <li>成功后保存响应并移除 running；失败只移除 running，不缓存失败。</li>
 *   <li>取消不存在的请求返回 false，不抛异常。</li>
 * </ul>
 */
@Component
public class EvaluationJobRegistry {

    private final Map<String, PersistentBotProcess> running = new ConcurrentHashMap<>();
    private final Map<String, EvaluationResponse> completed = new ConcurrentHashMap<>();

    public void register(String requestId, PersistentBotProcess process) {
        running.put(requestId, process);
    }

    public PersistentBotProcess getRunning(String requestId) {
        return running.get(requestId);
    }

    public boolean isRunning(String requestId) {
        return running.containsKey(requestId);
    }

    public void removeRunning(String requestId) {
        running.remove(requestId);
    }

    public EvaluationResponse getCompleted(String requestId) {
        return completed.get(requestId);
    }

    public void markCompleted(String requestId, EvaluationResponse response) {
        completed.put(requestId, response);
        running.remove(requestId);
    }

    public boolean contains(String requestId) {
        return running.containsKey(requestId) || completed.containsKey(requestId);
    }
}
