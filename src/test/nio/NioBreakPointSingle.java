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
    private String savePath = "/home/hooxin/temp.zip";

    public NioMTBreakPointDownloadClient() {
        try {
            decoder = Charset.forName("utf8").newDecoder();

            client = SocketChannel.open();
            selector = Selector.open();

            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_CONNECT);
            client.connect(new InetSocketAddress(ADDRESS, PORT));

            encoder = Charset.forName("utf8").newEncoder();
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
                if (!handlerKey(key)) {
                    break FOR;
                }
            }
            selector.selectedKeys().clear();
        }
    }

    private boolean handlerKey(SelectionKey key) {
        try {
            if (key.isConnectable()) {
                SocketChannel channel = (SocketChannel) key.channel();
                if (channel.isConnectionPending())
                    channel.finishConnect();
                NioMessage message = new NioMessage("download=true;filename=8269b1c0-e923-3da1-97b7-e15c4967ac3e.rar");
                message.writeMessage(channel);
                SelectionKey wKey=channel.register(selector, SelectionKey.OP_READ);
                wKey.attach(new ReadHandler());
            } else if (key.isReadable()) {
                SocketChannel channel = (SocketChannel) key.channel();
                ReadHandler readHandler= (ReadHandler) key.attachment();
                NioMessage message = readHandler.readBlock(channel);
                System.out.println("=========== client read ===========");
                if (message != null) {
                    if (message.isHasData()) {
                        readHandler.downloadToFile(message.getData());
                        if ("incomplete".equals(message.getMsg())) {
                            channel.register(selector,SelectionKey.OP_WRITE);
                            readHandler.command="incomplete";
                            key.attach(readHandler);
                        }
                    } else {
                        if ("complete".equals(message.getMsg())) {
                            readHandler.close();
                            channel.close();
                            client.close();
                            return false;
                        }
                    }
                }

            } else if (key.isWritable()) {
                SocketChannel channel = (SocketChannel) key.channel();
                ReadHandler handler = (ReadHandler) key.attachment();
                System.out.println("========== client write ==========");
                if (handler!=null) {
                    if ("complete".equals(handler.command)) {
                        NioMessage message = new NioMessage();
                        message.setMsg("complete");
                        message.writeMessage(channel);
                        channel.register(selector, SelectionKey.OP_READ);
                    } else if ("incomplete".equals(handler.command)) {
                        NioMessage message=new NioMessage("incomplete");
                        message.writeMessage(channel);
                        channel.register(selector, SelectionKey.OP_READ);
                    }
                    key.attach(handler);
                }

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

     class ReadHandler {
        ByteBuffer buffer;
        ByteArrayOutputStream bos;
        String command="";
        ReadHandler() {
            buffer = ByteBuffer.allocate(8*1024);
            bos = new ByteArrayOutputStream();
        }

        public NioMessage readBlock(SocketChannel channel) throws IOException {
            NioMessage message = null;
            buffer.clear();
            if (channel.read(buffer) > 0) {
                buffer.flip();

                bos.write(buffer.array());
                try {
                    message = NioMessage.readToMessage(bos.toByteArray());
                    bos.reset();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {

                }
            }
            return message;
        }

        public void close() {
            try {
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void downloadToFile(byte[] data) throws IOException {
            FileChannel fc = new FileOutputStream(savePath).getChannel();
            fc.position(downloadLength);
            fc.write(ByteBuffer.wrap(data));
            fc.close();
            downloadLength += data.length;
        }
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
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(this);
        channel.write(ByteBuffer.wrap(bos.toByteArray()));
        bos.close();
        oos.close();
    }
}
