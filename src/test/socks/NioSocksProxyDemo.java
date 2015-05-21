package test.socks;

import javax.security.auth.Subject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;
import java.util.stream.StreamSupport;

public class NioSocksProxyDemo {
  public static void main(String[] args) {
    NSocks5Server socks5Server = new NSocks5Server(9898);
    socks5Server.listen();
  }
}

class NSocks5Server {
  private Selector serverSelector;
  private ServerSocketChannel serverSocketChannel;
  private ByteBuffer buffer;
  private final static int BUFFER_LENGTH = 8 * 1024;
  private CharsetDecoder decoder;
  private CharsetEncoder encoder;

  private enum Step {
    RECEIVE(new byte[]{0x05, 0x01, 0x00, 0x00}),
    SEND(new byte[]{0x05, 0x00, 0x00, 0x00}),
    BIND(new byte[]{0x05, 0x01, 0x00, 0x01}),
    CONNECTED(new byte[]{0x05,0x00,0x00,0x03});

    private byte[] value;

    Step(byte[] bytes) {
      value = bytes;
    }

    public byte[] getValue() {
      return value;
    }

  }

  ;

  public NSocks5Server(int listenPort) {

    try {
      serverSocketChannel = ServerSocketChannel.open();
      serverSelector = Selector.open();
      serverSocketChannel.bind(new InetSocketAddress(listenPort));
      serverSocketChannel.configureBlocking(false);
      serverSocketChannel.register(serverSelector, SelectionKey.OP_ACCEPT);
      buffer = ByteBuffer.allocate(BUFFER_LENGTH);
      decoder = Charset.forName("utf8").newDecoder();
      encoder = Charset.forName("utf8").newEncoder();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void listen() {
    while (true) {
      try {
        serverSelector.select(1000);
        Iterator<SelectionKey> keys = serverSelector.selectedKeys().iterator();
        while (keys.hasNext()) {
          SelectionKey key = keys.next();
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
      if (key.isAcceptable() && key.channel() == serverSocketChannel) {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        try {
          SocketChannel channel = serverChannel.accept();
          channel.configureBlocking(false);
          channel.register(serverSelector, SelectionKey.OP_READ);
          SockStatus sockStatus=new SockStatus();
          key.attach(sockStatus);
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else if (key.isReadable()) {
        SocketChannel channel = (SocketChannel) key.channel();
        SockStatus sockStatus = (SockStatus) key.attachment();

        try {
          if (sockStatus == null)
            sockStatus = new SockStatus();
          int readcount = 0;
          if((readcount = channel.read(buffer)) > 0) {
            buffer.flip();
            if (!sockStatus.isConnected()) {
              System.out.println("read  isconnected is false");
              if (sockStatus.getStatus() == null) {
                System.out.println("read Step Receive");
                byte[] bytes = new byte[4];
                buffer.get(bytes, 0, readcount);
                if (ByteArrayEquals(Step.RECEIVE.getValue(), bytes)) {
                  channel.register(serverSelector, SelectionKey.OP_WRITE);
                  sockStatus.setStatus(Step.RECEIVE);
                  key.attach(sockStatus);
                }
              }
              else if (sockStatus.getStatus() == Step.SEND) {
                System.out.println("read Step Send");
                channel.register(serverSelector, SelectionKey.OP_WRITE);
                sockStatus.setData(buffer.array());
                key.attach(sockStatus);
              }
              else if (sockStatus.getStatus() == Step.BIND) {
                System.out.println("read Step Bind");
                channel.register(serverSelector,SelectionKey.OP_WRITE);

              }
            }
            buffer.clear();
          }
          else {
            System.out.println("read nothing ...");
            key.cancel();
            channel.close();
          }
//          key.cancel();
//          channel.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else if (key.isWritable()) {
        SocketChannel channel = (SocketChannel) key.channel();
        SockStatus sockStatus=null;
        Object attach=key.attachment();
        if(attach!= null && attach instanceof SockStatus)
          sockStatus= (SockStatus) attach;
        if(sockStatus!=null){

          if (!sockStatus.isConnected()) {
            if (sockStatus.getStatus() == Step.RECEIVE) {
              try {
                System.out.println("write receive===");
                channel.write(ByteBuffer.wrap(Step.SEND.getValue()));
                channel.register(serverSelector, SelectionKey.OP_READ);
                sockStatus=new SockStatus();
                sockStatus.setStatus(Step.SEND);
                key.attach(sockStatus);
              } catch (IOException e) {
                e.printStackTrace();
              }
            } else if (sockStatus.getStatus() == Step.SEND) {
              System.out.println("write send===");
              byte[] buf = sockStatus.getData();
              String ip = "";
              int port = 0;
              buffer.clear();
              buffer.put(Step.BIND.getValue());
              if ((buf[0] == Step.BIND.getValue()[0] && buf[1] == Step.BIND.getValue()[1] && buf[2] == Step.BIND.getValue()[2] && buf[3] == Step.BIND.getValue()[3])) {
                ip = (bytes2int(buf[4])) + "." + (bytes2int(buf[5])) + "." + (bytes2int(buf[6])) + "." + (bytes2int(buf[7]));
                port = buf[8] * 256 + buf[9];
              }
              else {
                ip = new String(buf);
                int endIdx=ip.indexOf("\0",5);
                ip=ip.substring(5,endIdx);
                port = buf[endIdx]*256+buf[endIdx+1];
              }
              try {
                buffer.put(ip.getBytes());
                buffer.putInt(port);
                channel.write(buffer);
                buffer.clear();
                buffer.put(buf);
                channel.write(buffer);
                channel.register(serverSelector, SelectionKey.OP_READ);
                sockStatus = new SockStatus(false,Step.BIND);
                key.attach(sockStatus);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
//            else if (sockStatus.getStatus() == Step.BIND) {
//              try {
//                channel.register(serverSelector,SelectionKey.OP_READ);
//                sockStatus=new SockStatus(false,Step.CONNECTED);
//                key.attach(sockStatus);
//              } catch (ClosedChannelException e) {
//                e.printStackTrace();
//              }
//
//            }
          }
        }
      }
    }

    private int bytes2int(byte b) { //byte转int
      int mask = 0xff;
      int temp = 0;
      int res = 0;
      res <<= 8;
      temp = b & mask;
      res |= temp;
      return res;
    }
    private boolean ByteArrayEquals(byte[] a, byte[] b) {
      if (a.length == b.length) {
        boolean result = true;
        for (int i = 0; i < a.length; i++) {
          if (a[i] != b[i]) {
            result = false;
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

  class SockStatus {
    private boolean isConnected;
    private Step status;
    private byte[] data;

    public byte[] getData() {
      return data;
    }

    public void setData(byte[] data) {
      this.data = data;
    }

    public SockStatus() {
      isConnected = false;
    }

    public SockStatus(boolean isConnected, Step status) {
      this.isConnected = isConnected;
      this.status = status;
    }

    public SockStatus(boolean isConnected, Step status, byte[] data) {
      this.isConnected = isConnected;
      this.status = status;
      this.data = data;
    }

    public boolean isConnected() {
      return isConnected;
    }

    public void setIsConnected(boolean isConnected) {
      this.isConnected = isConnected;
    }

    public Step getStatus() {
      return status;
    }

    public void setStatus(Step status) {
      this.status = status;
    }
  }

}

class Sock5Protocol {
  public enum Step {
    RECEIVE(new byte[]{0x05, 0x01, 0x00, 0x00}),
    SEND(new byte[]{0x05, 0x00, 0x00, 0x00}),
    BIND(new byte[]{0x05, 0x01, 0x00, 0x01});

    private byte[] value;

    Step(byte[] bytes) {
      value = bytes;
    }

    public byte[] getValue() {
      return value;
    }
  }

  ;
}

class Socks {

}