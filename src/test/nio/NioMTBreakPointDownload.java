package test.nio;

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

            for(;;) {
                selector.select();

                for (SelectionKey key : selector.selectedKeys()) {
                    if(!handlerKey(key)){
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public boolean handlerKey(SelectionKey key) {
        // TODO 实现事件处理
        return true;
    }

    class DownloadHandler {
        ByteBuffer buffer=ByteBuffer.allocate(1024*8);
        FileChannel fc;
        DownloadHandler() throws FileNotFoundException {
            fc=new FileInputStream(downloadFile).getChannel();
        }

        public void download(SocketChannel channel) throws IOException, ClassNotFoundException {
            int i = fc.read(buffer);
            if(i>0) {
                NioMessage message = new NioMessage("incomplete", true, buffer.array());
                message.writeMessage(channel);
            }
            else{
                NioMessage message=new NioMessage("complete");
            }
        }

        public void close() throws IOException {
            fc.close();
        }
    }
}

