package com.amqp.aliyun.spring.boot.starter.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "amqp.aliyun.properties")
public class AmqpClientProperties {

    private String appKey;
    private String appSecret;
    private String consumerGroupId;
    private String AmqpEndPointUrl;

    public String getAppKey() {
        return appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public String getConsumerGroupId() {
        return consumerGroupId;
    }

    public String getAmqpEndPointUrl() {
        return AmqpEndPointUrl;
    }

    public void setAmqpEndPointUrl(String amqpEndPointUrl) {
        AmqpEndPointUrl = amqpEndPointUrl;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public void setConsumerGroupId(String consumerGroupId) {
        this.consumerGroupId = consumerGroupId;
    }


}
