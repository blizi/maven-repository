package com.amqp.aliyun.spring.boot.starter.client;

import com.amqp.aliyun.spring.boot.starter.listener.AmqpMessageListener;
import org.apache.commons.codec.binary.Hex;
import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsConnectionListener;
import org.apache.qpid.jms.message.JmsInboundMessageDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.net.URI;
import java.util.Date;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AmqpJavaClient {

    private final static Logger logger = LoggerFactory.getLogger(AmqpJavaClient.class);
    private static AmqpMessageListener amqpMessageListener = null;

    //业务处理异步线程池
    private final static ExecutorService executorService = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 2, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50000));

    public AmqpJavaClient(AmqpMessageListener amqpMessageListener, String appKey, String appSecret, String consumerGroupId, String AMQPEndPointUrl) {
        AmqpJavaClient.amqpMessageListener = amqpMessageListener;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.consumerGroupId = consumerGroupId;
        this.AMQPEndPointUrl = AMQPEndPointUrl;
    }


    private final String appKey;
    private final String appSecret;
    private final String consumerGroupId;
    private final String AMQPEndPointUrl;

    public void init() throws Exception {
        long random = new Date().getTime();
        //建议使用机器UUID、MAC地址、IP等唯一标识等作为clientId。便于您区分识别不同的客户端。
        String clientId = Math.random() + "";

        String userName = clientId + "|authMode=appkey"
                + ",signMethod=" + "SHA256"
                + ",random=" + random
                + ",appKey=" + appKey
                + ",groupId=" + consumerGroupId + "|";
        String signContent = "random=" + random;
        String password = doSign(signContent, appSecret, "HmacSHA256");
        String connectionUrlTemplate = "failover:(" + AMQPEndPointUrl + "?amqp.idleTimeout=80000)"
                + "?failover.maxReconnectAttempts=10&failover.reconnectDelay=30";

        Hashtable<String, String> hashtable = new Hashtable<>();
        hashtable.put("connectionfactory.SBCF", connectionUrlTemplate);
        hashtable.put("queue.QUEUE", "default");
        hashtable.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        Context context = new InitialContext(hashtable);
        ConnectionFactory cf = (ConnectionFactory) context.lookup("SBCF");
        Destination queue = (Destination) context.lookup("QUEUE");
        // 创建连接。
        Connection connection = cf.createConnection(userName, password);
        ((JmsConnection) connection).addConnectionListener(myJmsConnectionListener);
        // 创建会话。
        // Session.CLIENT_ACKNOWLEDGE: 收到消息后，需要手动调用message.acknowledge()。
        // Session.AUTO_ACKNOWLEDGE: SDK自动ACK（推荐）。
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        connection.start();
        // 创建Receiver连接。
        MessageConsumer consumer = session.createConsumer(queue);
        consumer.setMessageListener(messageListener);
    }


    private static MessageListener messageListener = new MessageListener() {
        @Override
        public void onMessage(Message message) {
            try {
                //1.收到消息之后一定要ACK。
                // 推荐做法：创建Session选择Session.AUTO_ACKNOWLEDGE，这里会自动ACK。
                // 其他做法：创建Session选择Session.CLIENT_ACKNOWLEDGE，这里一定要调message.acknowledge()来ACK。
                // message.acknowledge();
                //2.建议异步处理收到的消息，确保onMessage函数里没有耗时逻辑。
                // 如果业务处理耗时过程过长阻塞住线程，可能会影响SDK收到消息后的正常回调。
                executorService.submit(() -> processMessage(message));
            } catch (Exception e) {
                logger.error("submit task occurs exception ", e);
            }
        }
    };


    /**
     * 在这里处理您收到消息后的具体业务逻辑。
     */
    private static void processMessage(Message message) {
        try {
            byte[] body = message.getBody(byte[].class);
            String content = new String(body);
            String topic = message.getStringProperty("topic");
            String messageId = message.getStringProperty("messageId");
            logger.info("receive message"
                    + ", topic = " + topic
                    + ", messageId = " + messageId
                    + ", content = " + content);
            amqpMessageListener.onMessage(topic, messageId, content);
        } catch (Exception e) {
            logger.error("processMessage occurs error ", e);
            logger.error("检查是否实现了接口 AmqpMessageListener", e);
        }
    }

    private static JmsConnectionListener myJmsConnectionListener = new JmsConnectionListener() {
        /**
         * 连接成功建立。
         */
        @Override
        public void onConnectionEstablished(URI remoteURI) {
            logger.info("onConnectionEstablished, remoteUri:{}", remoteURI);
            logger.info("链接建立成功");
        }

        /**
         * 尝试过最大重试次数之后，最终连接失败。
         */
        @Override
        public void onConnectionFailure(Throwable error) {
            logger.error("onConnectionFailure, {}", error.getMessage());
        }

        /**
         * 连接中断。
         */
        @Override
        public void onConnectionInterrupted(URI remoteURI) {
            logger.info("onConnectionInterrupted, remoteUri:{}", remoteURI);
        }

        /**
         * 连接中断后又自动重连上。
         */
        @Override
        public void onConnectionRestored(URI remoteURI) {
            logger.info("onConnectionRestored, remoteUri:{}", remoteURI);
        }

        @Override
        public void onInboundMessage(JmsInboundMessageDispatch envelope) {
        }

        @Override
        public void onSessionClosed(Session session, Throwable cause) {
        }

        @Override
        public void onConsumerClosed(MessageConsumer consumer, Throwable cause) {
        }

        @Override
        public void onProducerClosed(MessageProducer producer, Throwable cause) {
        }
    };

    /**
     * 计算签名，password组装方法，请参见AMQP客户端接入说明文档。
     */
    private static String doSign(String toSignString, String secret, String signMethod) throws Exception {
        SecretKeySpec signingKey = new SecretKeySpec(secret.getBytes(), signMethod);
        Mac mac = Mac.getInstance(signMethod);
        mac.init(signingKey);
        byte[] rawHmac = mac.doFinal(toSignString.getBytes());

        return Hex.encodeHexString(rawHmac);
    }
}
