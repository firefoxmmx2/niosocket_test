package test.nio;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
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
    private ByteBuffer buffer = ByteBuffer.allocate(1024);
    private boolean needConfig = true;

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
            while (selector.select() > 0) {
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (key.isConnectable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        if (channel.isConnectionPending()) {
                            channel.finishConnect();
                        }
                        channel.write(encoder.encode(CharBuffer.wrap("does support break point?")));
                        channel.register(selector, SelectionKey.OP_READ);
                    } else if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        buffer.clear();
                        int i = 0;
                        if ((i = channel.read(buffer)) > 0) {
                            buffer.flip();
                            try {
                                String result= decoder.decode(buffer).toString();
                                System.out.println("====" + result + "====");
                                if("complete=true".equals(result)){
                                    needConfig=false;
                                    break;
                                }
                                else if ("continue=true".equals(result)) {
                                    downloadOptionStr += ";" + result;
                                }

                                channel.register(selector, SelectionKey.OP_WRITE);
                            } catch (MalformedInputException e) {
                                RandomAccessFile f = new RandomAccessFile("/home/hooxin/temp.zip", "rw");
                                f.seek(downloadLength);
                                f.write(buffer.array());
                                buffer.clear();
                                downloadLength += i;
                                FileChannel fc = f.getChannel();
                                while ((i = channel.read(buffer)) > 0) {
                                    buffer.flip();
                                    fc.write(buffer);
                                    buffer.clear();
                                    downloadLength += i;
                                }
                                fc.close();
                            }

                        }

                    } else if (key.isWritable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        channel.write(encoder.encode(CharBuffer.wrap(downloadOptionStr)));
                        channel.register(selector, SelectionKey.OP_READ);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                selector.close();
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (needConfig) {
                File downloadConfigFile = new File(downloadConfigPath);
                try {
                    BufferedWriter bw = new BufferedWriter(new FileWriter(downloadConfigFile));
                    downloadOptionStr+=";downloaded="+downloadLength;
                    String[] lines = downloadOptionStr.split(";");
                    for (String line : lines) {
                        bw.write(line);
                        bw.newLine();
                    }
                    bw.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            else{
                File downloadConfigFile=new File(downloadConfigPath);
                downloadConfigFile.delete();
            }
        }
    }
}
