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
    private File downloadFile = new File("/home/hooxin/Downloads/第二讲：ExtJS.4中的新功能-定时事件与健盘导航.zip");
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
                        channel.register(selector,SelectionKey.OP_WRITE);
                        if (count > 0) {
                            buffer.flip();
                            String msg = decoder.decode(buffer).toString();
                            System.out.println("===================" + msg + "===================");
                            if(msg.contains("continue")){
                                boolean isContinue=false;
                                int downloadLength=0;
                                for (String s : msg.split(";")) {
                                    String[] vs=s.split("=");
                                    if("continue".equals(vs[0])&&"true".equals(vs[1])){
                                        isContinue=true;
                                    } else if ("downloaded".equals(vs[0])) {
                                        downloadLength=Integer.parseInt(vs[1]);
                                    }
                                }
                                key.attach(new DownloadHandler(isContinue,downloadLength,channel));
                            }
                            else {
                                key.attach("continue=true");
                            }
                        } else
                            channel.close();
                    } else if (key.isWritable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        Object handler= key.attachment();
                        if (handler instanceof String){
                            if("continue=true".equals(handler)){
                                channel.write(encoder.encode(CharBuffer.wrap("continue=true")));
                                channel.register(selector,SelectionKey.OP_READ);
                            }

                        } else if (handler instanceof DownloadHandler) {
                            DownloadHandler downloadHandler=(DownloadHandler) handler;
                            downloadHandler.download(downloadFile);
                            downloadHandler.close();
                        }
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

    class DownloadHandler {
        private boolean isContinue=false;
        private int downloadLength=0;
        private SocketChannel channel;
        private final int BLOCK_LEN = 4 * 1024;
        private ByteBuffer buffer=ByteBuffer.allocate(BLOCK_LEN);

        DownloadHandler(boolean isContinue, int downloadLength, SocketChannel channel) {
            this.isContinue = isContinue;
            this.downloadLength = downloadLength;
            this.channel = channel;
        }

        DownloadHandler() {
        }

        public void download(File file) throws IOException {
            RandomAccessFile f=new RandomAccessFile(file,"r");
            f.seek(downloadLength);
            FileChannel fc=f.getChannel();
            int writedLen=0;
            int i=0;
            while((i=fc.read(buffer))>0){
                buffer.flip();
                channel.write(buffer);
                buffer.clear();
                writedLen+=i;
            }
            System.out.println("======== Writed: " + writedLen / 1024 / 1024.0 + " Mbytes. ========");
            fc.close();
        }

        public void close() throws IOException {
            channel.close();
        }
    }


}

