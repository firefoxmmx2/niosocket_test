package test.mott;

import com.ibm.mqtt.Mqtt;
import com.ibm.mqtt.MqttClient;
import com.ibm.mqtt.MqttException;
import com.ibm.mqtt.MqttSimpleCallback;

/**
 * Created by hooxin on 14-6-16.
 */
public class MottClientTest {
    private final static String CONNECTION_URL = "tcp://localhost:1883";
    private final static boolean CLEAN_STRING = true;
    private final static short KEEP_ALIVE = 30;
    private final static String CLIENT_ID = "client1";
    private final static String[] TOPICS = {
            "Test/Test Topics/Topic1",
            "Test/Test Topics/Topic2",
            "Test/Test Topics/Topic3",
            "tokudu/client1"
    };
    private final static int[] QOS_VALUES = {0, 0, 2, 0};

    private MqttClient mqttClient = null;
    private final static String PUBLISH_TOPICS=TOPICS[0];

    public MottClientTest(String i) {
        try {
            mqttClient = new MqttClient(CONNECTION_URL);
            SimpleCallbackHandler simpleCallbackHandler = new SimpleCallbackHandler();
            mqttClient.registerSimpleHandler(simpleCallbackHandler);
            mqttClient.connect(CLIENT_ID + i, CLEAN_STRING, KEEP_ALIVE);
            mqttClient.subscribe(TOPICS, QOS_VALUES);
//            mqttClient.publish(PUBLISH_TOPICS, "keepalive".getBytes(), QOS_VALUES[0], true);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    class SimpleCallbackHandler implements MqttSimpleCallback {
        @Override
        public void connectionLost() throws Exception {
            System.out.println("客户机和broker已经断开");
        }

        @Override
        public void publishArrived(String topicName, byte[] payload, int qos, boolean retained) throws Exception {
            System.out.println("订阅主题：" + topicName);
            System.out.println("消息数据：" + new String(payload));
            System.out.println("消息级别（0,1,2）：" + qos);
            System.out.println("是否是实时发送的消息（false=实时，true=服务器上保留的最后消息）：" + retained);

        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) {
            new MottClientTest(String.valueOf(i));
        }

        MqttAdminClientTest adminClient=new MqttAdminClientTest();
        adminClient.publish("大家好");
        adminClient.publish("我是楼主");
        adminClient.publish("测试一下这个东西到底有没有毛病");
        adminClient.publish("看来还不错,不知道能不能自动关闭连接");
    }
}


class MqttAdminClientTest{
    private final static String CONNECTION_URL = "tcp://localhost:1883";
    private final static boolean CLEAN_STRING = true;
    private final static short KEEP_ALIVE = 30;
    private final static String CLIENT_ID = "admin";
    private final static String[] TOPICS = {
            "Test/Test Topics/Topic1",
            "Test/Test Topics/Topic2",
            "Test/Test Topics/Topic3",
            "tokudu/client1"
    };
    private final static int[] QOS_VALUES = {0, 0, 2, 0};

    private MqttClient mqttClient = null;
    private final static String PUBLISH_TOPICS=TOPICS[0];

    MqttAdminClientTest() {
        try {
            mqttClient=new MqttClient(CONNECTION_URL);
            mqttClient.connect(CLIENT_ID,CLEAN_STRING,KEEP_ALIVE);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publish(String msg) {
        try {
            mqttClient.publish(PUBLISH_TOPICS,msg.getBytes(),QOS_VALUES[0],true);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
