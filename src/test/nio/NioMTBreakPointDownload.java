package test.nio;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;

/**
 * Created by hooxin on 14-6-26.
 */
public class NioMTBreakPointDownload {
    public static void main(String[] args) {
        NioMTBreakPointDowndloadServer server = new NioMTBreakPointDowndloadServer();
        server.listen();
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
            selector = Selector.open();
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
            while (true) {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        SocketChannel channel = serverChannel.accept();
                        channel.configureBlocking(false);
                        channel.register(selector, SelectionKey.OP_READ);
                    } else if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        buffer.clear();
                        int count = channel.read(buffer);
                        System.out.println("====");
                        channel.register(selector,SelectionKey.OP_WRITE);
                        if (count > 0) {
                            buffer.flip();
                            String msg = decoder.decode(buffer).toString();
                            System.out.println("===================msg=" + msg + "===================");

                        } else
                            channel.close();
                    } else if (key.isWritable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        channel.write(encoder.encode(CharBuffer.wrap("hello world")));
                        channel.register(selector,SelectionKey.OP_READ);
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

