package test.socks;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;
import java.util.concurrent.RecursiveAction;

public class NioSocksProxyDemo {
}

class NSocks5Server {
  private Selector serverSelector;
  private ByteBuffer buffer;
  private final static int BUFFER_LENGTH=8*1024;
  private CharsetDecoder decoder;
  private CharsetEncoder encoder;

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
        serverSelector.select();
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
          while((readcount = channel.read(buffer)) != -1){

          }

        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      else if (key.isWritable()) {

      }
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
  private enum Step {
    METHOD_SELECTION,
    CONNECT,
    BIND
  };
}

class Socks {
}