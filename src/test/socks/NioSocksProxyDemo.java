package test.socks;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;

public class NioSocksProxyDemo {
  public static void main(String[] args) {
    NSocks5Server socks5Server=new NSocks5Server(9898);
    socks5Server.listen();
  }
}

class NSocks5Server {
  private Selector serverSelector;
  private ByteBuffer buffer;
  private final static int BUFFER_LENGTH=8*1024;
  private CharsetDecoder decoder;
  private CharsetEncoder encoder;
  private enum Step {
    RECEIVE(new byte[]{0x05, 0x01, 0x00, 0x00}),
    SEND(new byte[]{0x05, 0x01, 0x00, 0x01});

    private byte[] value;

    Step(byte[] bytes) {
      value = bytes;
    }

    public byte[] getValue() {
      return value;
    }

  };
  public NSocks5Server(int listenPort) {
    try {
      ServerSocketChannel serverSocketChannel=ServerSocketChannel.open();
      serverSelector=Selector.open();
      serverSocketChannel.bind(new InetSocketAddress(listenPort));
      serverSocketChannel.configureBlocking(false);
      serverSocketChannel.register(serverSelector, SelectionKey.OP_ACCEPT);
      buffer=ByteBuffer.allocate(BUFFER_LENGTH);
      decoder= Charset.forName("utf8").newDecoder();
      encoder=Charset.forName("utf8").newEncoder();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void listen() {
    while(true){
      try {
        serverSelector.select(1000);
        Iterator<SelectionKey> keys=serverSelector.selectedKeys().iterator();
        while(keys.hasNext()){
          SelectionKey key=keys.next();
          keys.remove();
          new NioSocks5ServerHandler(key);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }

    }
  }
  class NioSocks5ServerHandler implements Runnable {
    private SelectionKey key;
    private boolean isConnected=false;
    private Step State;

    public NioSocks5ServerHandler(SelectionKey key) {
      this.key = key;
      this.run();
    }

    @Override
    public void run() {
      if(key.isAcceptable()){
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        try {
          SocketChannel channel = serverChannel.accept();
          channel.configureBlocking(false);
          channel.register(serverSelector,SelectionKey.OP_READ);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      else if (key.isReadable()) {
        SocketChannel channel= (SocketChannel) key.channel();
        try {
          int readcount=0;
          while((readcount = channel.read(buffer)) > 0){
            buffer.flip();
            if(!isConnected){
              byte[] bytes=new byte[4];
              buffer.get(bytes, 0, readcount);
              if(State == null){
                if (ByteArrayEquals(Step.RECEIVE.getValue(),bytes)) {
                  ByteBuffer nbuff = ByteBuffer.allocate(10);
                  nbuff.put(Step.SEND.getValue());
                  InetSocketAddress remoteAddress=((InetSocketAddress) channel.getRemoteAddress());
                  nbuff.put(remoteAddress.getAddress().getAddress());
                  nbuff.put((byte) (remoteAddress.getPort() * 0xff >>> 8));
                  nbuff.put((byte) (remoteAddress.getPort() / 0xff));
                  channel.write(nbuff);
                  nbuff.clear();
                  State = Step.RECEIVE;
                }
              }
              if(State == Step.SEND){

              }
            }
            System.out.println(decoder.decode(buffer));
            buffer.clear();
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    private boolean ByteArrayEquals(byte[] a,byte[] b){
      if(a.length == b.length){
        boolean result=true;
        for (int i = 0; i < a.length; i++) {
          if(a[i] != b[i]) {
            result=false;
            break;
          }

        }
        return result;
      }
      return false;
    }
  }
  class NioSocks5Client implements Runnable {
    final SocketChannel client;
    public NioSocks5Client(SocketChannel client) {
      this.client = client;
    }

    @Override
    public void run() {

    }
  }


}

class Sock5Protocol {
  public enum Step {
    RECEIVE(new byte[]{0x05,0x01,0x00,0x00}),
    SEND(new byte[]{0x05,0x00,0x00,0x00}),
    BIND(new byte[]{0x05,0x01,0x00,0x01});

    private byte[] value;
    Step(byte[] bytes) {
      value=bytes;
    }

    public byte[] getValue() {
      return value;
    }
  };
}

class Socks {

}