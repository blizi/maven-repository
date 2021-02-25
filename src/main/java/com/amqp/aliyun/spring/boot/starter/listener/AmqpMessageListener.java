package com.amqp.aliyun.spring.boot.starter.listener;

public interface AmqpMessageListener {
    void onMessage(String topic, String messageId, String content);
}
