package test.nio;

import test.common.StringUtils;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.Buffer;
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
    private final int BLOCK_LEN = 8 * 1024;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private ByteBuffer buffer;
    private String downloadDirPath = "/home/hooxin/Downloads";
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

            for (; ; ) {
                selector.select();

                for (SelectionKey key : selector.selectedKeys()) {
                    if (!handlerKey(key)) {
                        break;
                    }
                }
                selector.selectedKeys().clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    public boolean handlerKey(SelectionKey key) throws IOException, ClassNotFoundException {
        if (key.isAcceptable()) {
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            SocketChannel channel = server.accept();
            channel.configureBlocking(false);
            SelectionKey mKey = channel.register(selector, SelectionKey.OP_READ);
            mKey.attach(new ReadHandler());
        } else if (key.isReadable()) {
            SocketChannel channel = (SocketChannel) key.channel();
            ReadHandler handler = (ReadHandler) key.attachment();
            NioMessage message = handler.readBlock(channel);
            if (message != null) {
                if (StringUtils.isNotEmpty(message.getMsg())) {
                    boolean needDownload = false;
                    String filename = null;
                    for (String s : message.getMsg().split(";")) {
                        if ("download=true".equals(s)) {
                            needDownload = true;
                        } else if (s.contains("filename=")) {
                            filename = s.split("=")[1];
                        }
                    }

                    if (needDownload) {
                        channel.register(selector, SelectionKey.OP_WRITE);
                        key.attach(new DownloadHandler(new File(downloadDirPath + File.separator + filename)));
                    }
                }

            }
        } else if (key.isWritable()) {
            SocketChannel channel = (SocketChannel) key.channel();
            Object obj = key.attachment();
            if (obj instanceof DownloadHandler) {
                DownloadHandler downloadHandler = (DownloadHandler) obj;
                if (downloadHandler.download(channel)) {
                    downloadHandler.close();
                    channel.register(selector, SelectionKey.OP_READ);
                }

            }

        }
        return true;
    }

    class DownloadHandler {
        ByteBuffer buffer = ByteBuffer.allocate(1024 * 8);
        FileChannel fc;
        File file;

        DownloadHandler(File file) throws FileNotFoundException {
            fc = new FileInputStream(file).getChannel();
        }

        public boolean download(SocketChannel channel) throws IOException, ClassNotFoundException {
            int i = fc.read(buffer);
            if (i > 0) {
                NioMessage message = new NioMessage("incomplete", true, buffer.array());
                message.writeMessage(channel);
                return false;
            } else {
                return true;
            }

        }

        public void close() throws IOException {
            fc.close();
        }
    }

    class ReadHandler {
        ByteBuffer buffer;
        ByteArrayOutputStream bos;

        ReadHandler() {
            buffer = ByteBuffer.allocate(BLOCK_LEN);
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
    }
}

