package com.amqp.aliyun.spring.boot.starter.config;

import com.amqp.aliyun.spring.boot.starter.client.AmqpJavaClient;
import com.amqp.aliyun.spring.boot.starter.listener.AmqpMessageListener;
import com.amqp.aliyun.spring.boot.starter.properties.AmqpClientProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
@EnableConfigurationProperties(AmqpClientProperties.class)
@ConditionalOnProperty(
        prefix = "amqp.aliyun.config",
        name = "enable",
        havingValue = "true"
)
public class AmqpClientConfig implements InitializingBean {

    @Resource
    private AmqpClientProperties amqpClientProperties;

    @Resource
    private AmqpMessageListener amqpMessageListener;


    @Override
    public void afterPropertiesSet() throws Exception {
        AmqpJavaClient amqpJavaClient = new AmqpJavaClient(amqpMessageListener, amqpClientProperties.getAppKey(), amqpClientProperties.getAppSecret(), amqpClientProperties.getConsumerGroupId(), amqpClientProperties.getAmqpEndPointUrl());
        amqpJavaClient.init();
    }
}
