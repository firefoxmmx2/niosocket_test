package test.nio;

import java.awt.*;
import java.io.IOException;
import java.io.RandomAccessFile;
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
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by hooxin on 14-6-11.
 */
public class NioClientTest {
    static int SIZE=1;
    public static void main(String[] args) throws IOException, InterruptedException {
        ExecutorService exec= Executors.newFixedThreadPool(SIZE);
        for(int index=0;index<SIZE;index++){
            exec.execute(new NioClient.Download(index));
        }
//        Thread.sleep(10000);
        exec.shutdown();
    }
}

class NioClient{

    static int PORT=12345;
    static InetSocketAddress ip=new InetSocketAddress("localhost",PORT);
    static CharsetEncoder encoder= Charset.forName("utf8").newEncoder();
    static CharsetDecoder decoder= Charset.forName("utf8").newDecoder();
    static class Download implements Runnable{
        protected int index;

        Download(int index) {
            this.index = index;
        }

        @Override
        public void run() {
            try {
                long start=System.currentTimeMillis();
                SocketChannel client=SocketChannel.open();
                client.configureBlocking(false);
                Selector selector=Selector.open();
                client.register(selector, SelectionKey.OP_CONNECT);
                client.connect(ip);
                ByteBuffer buffer=ByteBuffer.allocate(8*1024);
                long total=0;
                FOR: for(;;){
                    selector.select();
                    Iterator<SelectionKey> iter=selector.selectedKeys().iterator();
                    while (iter.hasNext()){
                        SelectionKey key=iter.next();
                        iter.remove();
                        if(key.isConnectable()){
                            SocketChannel channel= (SocketChannel) key.channel();
                            if(channel.isConnectionPending())
                                channel.finishConnect();
                            channel.write(encoder.encode(CharBuffer.wrap("Hello from "+index)));
                            channel.register(selector,SelectionKey.OP_READ);
                        }
                        else if(key.isReadable()){
                            SocketChannel channel= (SocketChannel) key.channel();
                            buffer.clear();
                            int count=channel.read(buffer);
                            if(count>0){
                                RandomAccessFile f=new RandomAccessFile("/home/hooxin/testbookmarks.txt","rw");
                                FileChannel fc=f.getChannel();
                                fc.position(total);
                                buffer.flip();
                                fc.write(buffer);
                                buffer.clear();
                                total+=count;
                                while((count=channel.read(buffer))>0){
                                    buffer.flip();
                                    fc.write(buffer);
                                    buffer.clear();
                                    total+=count;
                                }
                                fc.close();
                                SelectionKey wKey= channel.register(selector,SelectionKey.OP_WRITE);
                                wKey.attach(new InComplete());
                            }
                            else{
                                client.close();
                                break FOR;
                            }
                        } else if (key.isWritable()) {
                            SocketChannel channel= (SocketChannel) key.channel();
                            Object obj=key.attachment();
                            if (obj instanceof InComplete) {
                                InComplete inComplete = (InComplete) obj;
                                channel.write(encoder.encode(CharBuffer.wrap("incomplete")));
                                SelectionKey wKey=channel.register(selector,SelectionKey.OP_READ);
                                wKey.attach(new InComplete());
                            }
                        }
                    }
                }

                double last=(System.currentTimeMillis()-start) * 1.0 / 1000;
                System.out.println("======Thread "+index+" download "+total/1024/1024.0+" Mbytes in "+last+" s.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class InComplete {

    }
}