package com.kob.service.impl.utils;


import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BotPool extends Thread {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Queue<Bot> bots = new LinkedList<>();

    public void addBot(Integer userId, String botCode, String input) {
        lock.lock();
        try {
            bots.add(new Bot(userId, botCode, input));
            condition.signalAll(); //唤醒所有线程
        } finally {
            lock.unlock();
        }
    }

    protected void consume(Bot bot) {
        Consumer consumer = new Consumer();
        consumer.startTimeout(2000, bot);
    }

    @Override
    public void run() {
        while (true) {
            Bot bot;
            lock.lock();
            try {
                while (bots.isEmpty()) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;  // 退出线程
                    }
                }
                bot = bots.remove();
            } finally {
                lock.unlock();  // 成对释放，避免锁计数泄漏（审计 1.3）
            }
            consume(bot);  // 锁外执行，不阻塞生产者
        }
    }
}

