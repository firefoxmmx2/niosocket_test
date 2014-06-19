package test.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

/**
 * Created by hooxin on 14-6-19.
 */
public class BaiduReader {
    private Charset charset=Charset.forName("GBK");
    private SocketChannel channel;
    public void readHTMLContent(){
        try {
            InetSocketAddress socketAddress=new InetSocketAddress("www.baidu.com",80);
            channel=SocketChannel.open();
            channel.connect(socketAddress);
            channel.write(charset.encode("GET / HTTP/1.1\r\n\r\n"));
            ByteBuffer buffer=ByteBuffer.allocate(1024);
            while (channel.read(buffer)!=-1){
                buffer.flip();
                System.out.println(charset.decode(buffer));
                buffer.clear();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new BaiduReader().readHTMLContent();
    }
}
