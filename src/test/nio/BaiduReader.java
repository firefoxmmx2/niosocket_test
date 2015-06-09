package test.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Iterator;

/**
 * Created by hooxin on 14-6-19.
 */
public class BaiduReader {
    private Charset charset=Charset.forName("GBK");
    private SocketChannel channel;
    private Selector selector;
    private ByteBuffer buffer=ByteBuffer.allocate(1024);

    public void readHTMLContent(){

        try {
            InetSocketAddress socketAddress=new InetSocketAddress("www.baidu.com",80);
            selector = Selector.open();
            channel=SocketChannel.open();
            channel.configureBlocking(false);
            channel.connect(socketAddress);
            channel.register(selector, SelectionKey.OP_CONNECT);

//            channel.write(charset.encode("GET / HTTP/1.1\r\n\r\n"));
//            while (channel.read(buffer)!=-1){
//                buffer.flip();
//                System.out.println(charset.decode(buffer));
//                buffer.clear();
//            }

            while(true){
                selector.select(200);
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while(keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    handler(key);
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

    public void handler(SelectionKey key) throws IOException {
        if(key.isValid() && key.isConnectable()){
            System.out.println("正在连接状态....");
            SocketChannel channel = (SocketChannel) key.channel();
            if(channel.isConnectionPending())
                channel.finishConnect();
            channel.register(selector,SelectionKey.OP_WRITE);
        }
        else if (key.isValid() && key.isReadable()){
            System.out.println("正在读取状态...");
            SocketChannel channel= (SocketChannel) key.channel();
            int readCount=channel.read(buffer);
            System.out.println("从网站通道读取了"+readCount+"直接的数据...");
            if(readCount >0 ) {
                buffer.flip();
                System.out.println("buffer content is " + charset.decode(buffer));
                buffer.clear();
                channel.register(selector,SelectionKey.OP_READ);
            }
            else{
                key.cancel();
                channel.close();
                throw new IOException("关闭selector");
            }
        }
        else if ( key.isValid() && key.isWritable()){
            System.out.println("正在写入通道状态...");
            SocketChannel channel= (SocketChannel) key.channel();
            buffer.put(charset.encode("GET / HTTP/1.1\r\n\r\n"));
            buffer.flip();
            channel.write(buffer);
            buffer.clear();
            channel.register(selector,SelectionKey.OP_READ);
        }
    }

    public static void main(String[] args) {
        new BaiduReader().readHTMLContent();
    }
}
