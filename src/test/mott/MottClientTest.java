package test.mott;

import com.ibm.mqtt.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by hooxin on 14-6-16.
 */
public class MottClientTest {
    private final static String CONNECTION_URL = "tcp://localhost:1883";
    private final static boolean CLEAN_STRING = false;
    private final static short KEEP_ALIVE = 30;
    private final static String CLIENT_ID = "client";
    private final static String[] TOPICS = {
            "topic_bjxx_aaaa4",
            "topic_bjxx_YTJ201",
            "topic_bjxx_YTJ200",
            "topic_bjxx_YTJ123456987"
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
            mqttClient.publish(PUBLISH_TOPICS, "keepalive".getBytes(), QOS_VALUES[0], true);
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

    public void close(){
        try {
            mqttClient.disconnect();
            mqttClient.terminate();
        } catch (MqttPersistenceException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        List<MottClientTest> clients=new ArrayList<MottClientTest>();
//        for (int i = 0; i < 1000; i++) {
//            clients.add(new MottClientTest(String.valueOf(i)));
//        }

        new MottClientTest("1");

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

    public void close() {
        try {
            mqttClient.disconnect();
            mqttClient.terminate();
        } catch (MqttPersistenceException e) {
            e.printStackTrace();
        }
    }
}
