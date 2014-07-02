package test.nio;

import com.sun.xml.internal.bind.v2.util.ByteArrayOutputStreamEx;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.MalformedInputException;
import java.util.Iterator;

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
    private SocketChannel channel;
    private Selector selector;
    private CharsetEncoder encoder;
    private CharsetDecoder decoder;
    private int downloadLength = 0;
    private String downloadConfigPath = "/tmp/downloadConfig";
    private String downloadOptionStr = "filename=scalaidea";
    private ByteBuffer buffer = ByteBuffer.allocate(1024 * 4);
    private boolean needConfig = true;
    private ByteArrayOutputStream bos;

    public NioMTBreakPointDownloadClient() {
        try {
            decoder = Charset.forName("utf8").newDecoder();

            File downloadConfigFile = new File(downloadConfigPath);
            if (downloadConfigFile.isFile()) {
                BufferedReader br = new BufferedReader(new FileReader(downloadConfigFile));
                String line = null;
                while ((line = br.readLine()) != null) {
                    String[] vs = line.split("=");
                    if ("downloaded".equals(vs[0])) {
                        downloadLength = Integer.parseInt(vs[1]);
                    }
                    downloadOptionStr += line + ";";
                }
                downloadOptionStr = downloadOptionStr.substring(0, downloadOptionStr.length() - 1);
                br.close();

            }
            channel = SocketChannel.open();
            selector = Selector.open();

            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_CONNECT);
            channel.connect(new InetSocketAddress(ADDRESS, PORT));

            encoder = Charset.forName("utf8").newEncoder();
            bos=new ByteArrayOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                selector.close();
                channel.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

    }

    public void listen() {
        try {
            FOR:
            for (; ; ) {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (key.isConnectable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        if (channel.isConnectionPending()) {
                            channel.finishConnect();
                        }
                        NioMessage message = new NioMessage(false, null, "breakpoint?");
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(bos);
                        oos.writeObject(message);
                        byte[] data = bos.toByteArray();
                        bos.close();
                        oos.close();
                        channel.write(ByteBuffer.wrap(data));
                        channel.register(selector, SelectionKey.OP_READ);
                    } else if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        buffer.clear();
                        int i = 0;
                        while ((i = channel.read(buffer)) > 0) {
                            buffer.flip();
                            bos.write(buffer.array());
                            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
                            NioMessage message=null;
                            try{
                                message = (NioMessage) ois.readObject();
                                bos.reset();
                            }catch (IOException e){

                            }
                            buffer.clear();
                            ois.close();
                            if(message!=null){
                                if (message.isData()) {
                                    FileChannel fc = new RandomAccessFile("/home/hooxin/temp.zip", "rw").getChannel();
                                    fc.position(downloadLength);
                                    fc.write(ByteBuffer.wrap(message.getData()));
                                    buffer.clear();
                                    downloadLength += i;
                                    fc.close();
                                } else {
                                    String result = message.getMsg();
                                    System.out.println("====" + result + "====");
                                    if ("complete=true".equals(result)) {
                                        needConfig = false;
                                        channel.close();
                                        break FOR;
                                    } else if ("continue=true".equals(result)) {
                                        downloadOptionStr += ";" + result;
                                    }

                                    SelectionKey wKey = channel.register(selector, SelectionKey.OP_WRITE);
                                    wKey.attach("continue=true");
                                }
                            }

                        }
//                        if( i <= 0)
//                        {
//                            needConfig=false;
//                            channel.close();
//                            break FOR;
//                        }

                    } else if (key.isWritable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        Object obj = key.attachment();
                        if (obj instanceof String) {
                            String s = (String) obj;
                            if ("continue=true".equals(s)) {
                                NioMessage message = new NioMessage(false, null, downloadOptionStr);
                                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                ObjectOutputStream oos = new ObjectOutputStream(bos);
                                oos.writeObject(message);
                                byte[] data = bos.toByteArray();
                                bos.close();
                                oos.close();
                                channel.write(ByteBuffer.wrap(data));
                                channel.register(selector, SelectionKey.OP_READ);
                            }
//                            else if ("complete=true".equals(s)) {
//                                NioMessage message=new NioMessage(false,null,"complete=true");
//                                ByteArrayOutputStream bos=new ByteArrayOutputStream();
//                                ObjectOutputStream oos=new ObjectOutputStream(bos);
//                                oos.writeObject(message);
//                                byte[] data=bos.toByteArray();
//                                bos.close();
//                                oos.close();
//
//                                channel.write(ByteBuffer.wrap(data));
//                                channel.register(selector,SelectionKey.OP_READ);
//                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                selector.close();
                channel.close();
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (needConfig) {
                File downloadConfigFile = new File(downloadConfigPath);
                try {
                    BufferedWriter bw = new BufferedWriter(new FileWriter(downloadConfigFile));
                    downloadOptionStr += ";downloaded=" + downloadLength;
                    String[] lines = downloadOptionStr.split(";");
                    for (String line : lines) {
                        bw.write(line);
                        bw.newLine();
                    }
                    bw.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } else {
                File downloadConfigFile = new File(downloadConfigPath);
                downloadConfigFile.delete();
            }
        }
    }
}

class NioMessage implements Serializable {
    private boolean isData = false;
    private byte[] data;
    private String msg;

    NioMessage(boolean isData, byte[] data, String msg) {
        this.isData = isData;
        this.data = data;
        this.msg = msg;
    }

    public boolean isData() {
        return isData;
    }

    public void setData(boolean isData) {
        this.isData = isData;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
