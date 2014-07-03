package test.nio;

import com.sun.xml.internal.fastinfoset.util.CharArray;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

/**
 * Created by hooxin on 14-6-26.
 */
public class NioBreakPointSingle {
    public static void main(String[] args) {
        NioMTBreakPointDownloadClient client = new NioMTBreakPointDownloadClient();
        client.listen();
    }
}

class NioMTBreakPointDownloadClient {
    private final int PORT = 10088;
    private final String ADDRESS = "127.0.0.1";
    private SocketChannel client;
    private Selector selector;
    private CharsetEncoder encoder;
    private CharsetDecoder decoder;
    private int downloadLength = 0;
    private ByteArrayOutputStream bos;
    private String savePath="/home/hooxin/temp.zip";

    public NioMTBreakPointDownloadClient() {
        try {
            decoder = Charset.forName("utf8").newDecoder();

            client = SocketChannel.open();
            selector = Selector.open();

            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_CONNECT);
            client.connect(new InetSocketAddress(ADDRESS, PORT));

            encoder = Charset.forName("utf8").newEncoder();
            bos = new ByteArrayOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                selector.close();
                client.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

    }

    public void listen() {
        FOR:
        for (; ; ) {
            try {
                selector.select();
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (SelectionKey key : selector.selectedKeys()) {
                if(!handlerKey(key)){
                    break FOR;
                }
            }
            selector.selectedKeys().clear();
        }
    }

    private boolean handlerKey(SelectionKey key)  {
        try{
            if (key.isConnectable()) {
                SocketChannel channel = (SocketChannel) key.channel();
                if (channel.isConnectionPending())
                    channel.finishConnect();
                NioMessage message = new NioMessage("download=true;filename=scala-intellij-bin-0.33.421.zip");
                message.writeMessage(channel);
                key.interestOps(key.interestOps() | SelectionKey.OP_READ);
            } else if (key.isReadable()) {
                SocketChannel channel = (SocketChannel) key.channel();
                ByteBuffer buffer = ByteBuffer.allocate(1024 * 8);
                buffer.clear();
                if (channel.read(buffer) > 0) {
                    NioMessage message=null;
                    bos.write(buffer.array());
                    try {
                        message = NioMessage.readToMessage(bos.toByteArray());
                        bos.reset();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {

                    }
                    if(message!=null){
                        if(message.isHasData()){
                            FileChannel fc=new FileOutputStream(savePath).getChannel();
                            fc.position(downloadLength);
                            fc.write(ByteBuffer.wrap(message.getData()));
                            fc.force(true);
                            fc.close();
                            downloadLength += message.getData().length;
                            if("complete".equals(message.getMsg())){
                                channel.register(selector,SelectionKey.OP_WRITE);
                                key.attach("complete");
                            }
                        }
                        else{
                            if("complete".equals(message.getMsg())){
                                channel.register(selector,SelectionKey.OP_WRITE);
                                key.attach("complete");
                            }
                        }
                    }
                }
                else{
                    channel.close();
                    client.close();
                    return false;
                }

            } else if (key.isWritable()) {
                SocketChannel channel= (SocketChannel) key.channel();
                Object obj=key.attachment();
                if (obj instanceof String) {
                    String s = (String) obj;
                    if("complete".equals(s)){
                        NioMessage message=new NioMessage();
                        message.setMsg("complete");
                        message.writeMessage(channel);
                        channel.register(selector,SelectionKey.OP_READ);
                    }
                }

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
       return true;
    }
}

class NioMessage implements Serializable {
    private String msg;
    private boolean hasData = false;
    private byte[] data;

    NioMessage(String msg, boolean hasData, byte[] data) {
        this.msg = msg;
        this.hasData = hasData;
        this.data = data;
    }

    NioMessage() {
    }

    NioMessage(String msg) {
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public boolean isHasData() {
        return hasData;
    }

    public void setHasData(boolean hasData) {
        this.hasData = hasData;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public static NioMessage readToMessage(byte[] data) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
        NioMessage message = (NioMessage) ois.readObject();
        ois.close();
        return message;
    }

    public void writeMessage(SocketChannel channel) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(channel.socket().getOutputStream());
        oos.writeObject(this);
        oos.close();
    }
}
