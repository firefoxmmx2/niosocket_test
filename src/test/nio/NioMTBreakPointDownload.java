package test.nio;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

/**
 * Created by hooxin on 14-6-26.
 */
public class NioMTBreakPointDownload {
    public static void main(String[] args) {
        new Thread() {
            @Override
            public void run() {
                NioMTBreakPointDowndloadServer server = new NioMTBreakPointDowndloadServer();
                server.listen();
            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                NioMTBreakPointDownloadClient client = new NioMTBreakPointDownloadClient();
                client.listen();
            }
        }.start();
    }
}


class NioMTBreakPointDowndloadServer {
    private final int PORT = 10088;
    private final int BLOCK_LEN = 4 * 1024;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private ByteBuffer buffer;
    private File downloadFile = new File("/home/hooxin/Downloads/scala-intellij-bin-0.33.421.zip");
    private CharsetDecoder decoder;
    private CharsetEncoder encoder;

    public NioMTBreakPointDowndloadServer() {
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(PORT));
            serverSocketChannel.configureBlocking(false);
            selector=Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            buffer = ByteBuffer.allocate(BLOCK_LEN);

            decoder = Charset.forName("utf8").newDecoder();
            encoder = Charset.forName("utf8").newEncoder();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                serverSocketChannel.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

    }


    public void listen() {
        try {
            while (selector.select() > 0) {
                for (SelectionKey key : selector.selectedKeys()) {
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        SocketChannel channel = serverChannel.accept();
                        channel.configureBlocking(false);
                        channel.register(selector, SelectionKey.OP_READ);
                    } else if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        buffer.clear();
                        channel.read(buffer);
                        String msg = decoder.decode(buffer).toString();
                        System.out.println("===================msg=" + msg + "===================");
                        channel.configureBlocking(false);
                        buffer.flip();
//                        channel.register(selector,SelectionKey.OP_WRITE);
                    } else if (key.isWritable()) {

                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                selector.close();
                serverSocketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}

class NioMTBreakPointDownloadClient {
    private final int PORT = 10088;
    private final String ADDRESS = "127.0.0.1";
    private SocketChannel channel;
    private Selector selector;
    private CharsetEncoder encoder;

    public NioMTBreakPointDownloadClient() {
        try {
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
                for (SelectionKey key : selector.selectedKeys()) {
                    if (key.isConnectable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        if (channel.isConnectionPending()) {
                            channel.finishConnect();
                        }
                        channel.write(encoder.encode(CharBuffer.wrap("download=true")));
                        channel.register(selector, SelectionKey.OP_READ);
                        channel.configureBlocking(false);
                    } else if (key.isReadable()) {

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
        }
    }
}
