package test.socks;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;

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
    SEND(new byte[]{0x05, 0x00}),
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
          SocksMessage socksMessage =new SocksMessage();
          key.attach(socksMessage);
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else if (key.isReadable()) {
        SocketChannel channel = (SocketChannel) key.channel();
        SocksMessage socksMessage = (SocksMessage) key.attachment();

        try {
          if (socksMessage == null)
            socksMessage = new SocksMessage();
          int readcount = 0;
          if((readcount = channel.read(buffer)) > 0) {
            buffer.flip();
            if (!socksMessage.isConnected()) {
              System.out.println("read  isconnected is false");
              if (socksMessage.getStatus() == Step.RECEIVE) {
                System.out.println("读取SOCKS 请求信息");
                int ver=buffer.get();
                int authTypeMethodLength=buffer.get();
                byte[] authTypeIntValueBytes=new byte[authTypeMethodLength];
                buffer.get(authTypeIntValueBytes);
                Socks.AuthType authType= Socks.AuthType.valueOf(authTypeIntValueBytes[0]);
                socksMessage.setVer(Socks.Vers.valueOf(ver));
                socksMessage.setAuthType(authType);
                channel.register(serverSelector, SelectionKey.OP_WRITE);
                key.attach(socksMessage);
              }
              else if (socksMessage.getStatus() == Step.SEND) {
                System.out.println("读取SOCKS验证请求");
                channel.register(serverSelector, SelectionKey.OP_WRITE);
                socksMessage.setData(buffer.array());
                key.attach(socksMessage);
              }
              else if (socksMessage.getStatus() == Step.BIND) {
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
        SocksMessage socksMessage =null;
        Object attach=key.attachment();
        if(attach!= null && attach instanceof SocksMessage)
          socksMessage = (SocksMessage) attach;
        if(socksMessage !=null){

          if (!socksMessage.isConnected()) {
            if (socksMessage.getStatus() == Step.RECEIVE) {
              try {
                System.out.println("响应 SOCKS 验证回复");
                ByteBuffer responseBuf=ByteBuffer.allocate(2);
                responseBuf.put(socksMessage.getVer().getValue().byteValue());
                if(socksMessage.getAuthType() == Socks.AuthType.NONE
                    || socksMessage.getAuthType() == Socks.AuthType.NO_ACCEPTABLE) {
                  System.out.println("未使用验证");
                  responseBuf.put(new Integer(0x00).byteValue());
                }
                else if(socksMessage.getAuthType() == Socks.AuthType.USER_PWD){
                  // TODO 用户验证
                  System.out.println("用户验证");
                }
                else if (socksMessage.getAuthType() == Socks.AuthType.GSSAPI) {
                  // TODO 通用安全服务应用程序接口 验证
                  System.out.println("通用安全服务应用程序接口验证");
                }
                else if (socksMessage.getAuthType() == Socks.AuthType.IANA) {
                  // TODO IANA 分配认证
                  System.out.println("IANA 分配认证");
                }

                channel.write(responseBuf);
                channel.register(serverSelector, SelectionKey.OP_READ);
                socksMessage.setStatus(Step.SEND);
                key.attach(socksMessage);
              } catch (IOException e) {
                e.printStackTrace();
              }
            } else if (socksMessage.getStatus() == Step.SEND) {
              System.out.println("write send===");
              byte[] buf = socksMessage.getData();
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
                socksMessage.setData(null);
                socksMessage.setStatus(Step.BIND);
                key.attach(socksMessage);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
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

  class SocksMessage {
    private boolean isConnected;
    private Step status=Step.RECEIVE;
    private byte[] data;
    private Socks.Vers ver;
    private Socks.AddressType addressType;
    private Socks.AuthType authType;

    public Socks.AuthType getAuthType() {
      return authType;
    }

    public void setAuthType(Socks.AuthType authType) {
      this.authType = authType;
    }

    public Socks.Vers getVer() {
      return ver;
    }

    public void setVer(Socks.Vers ver) {
      this.ver = ver;
    }

    public Socks.AddressType getAddressType() {
      return addressType;
    }

    public void setAddressType(Socks.AddressType addressType) {
      this.addressType = addressType;
    }

    public byte[] getData() {
      return data;
    }

    public void setData(byte[] data) {
      this.data = data;
    }

    public SocksMessage() {
      isConnected = false;
    }

    public SocksMessage(boolean isConnected, Step status) {
      this.isConnected = isConnected;
      this.status = status;
    }

    public SocksMessage(boolean isConnected, Step status, byte[] data) {
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
  /**
   * 版本
   */
  public enum Vers {
    V5(0x05),
    V4a(0x04);

    private Integer value;

    Vers(Integer i) {
      value=i;
    }

    public Integer getValue() {
      return value;
    }

    public static Vers valueOf(int verIntValue) {
      Vers ver = null;
      switch (verIntValue) {
        case 0x05:
          ver= V5;
          break;
        case 0x04:
          ver= V4a;
          break;
        default:
          ver = null;
          break;
      }
      return ver;
    }
  };

  /**
   * 地址类型
   */
  public enum AddressType {
    IPV4(1),
    IPV6(4),
    DOMAIN(3);

    private int value;
    AddressType(int i) {
      value = i;
    }

    public int getValue() {
      return value;
    }
  }

  public enum AuthType {
    /**
     * 0x00 无验证
     */
    NONE,
    /**
     * 0x01 通用安全服务应用程序接口(GSSAPI)
     */
    GSSAPI,
    /**
     * 0x02 用户名/密码
     */
    USER_PWD,
    /**
     * 0x03 ~ 0x7f IANA 分配认证
     */
    IANA,
    /**
     * 0x80 ~ 0xfe 私人方法保留
     */
    RESERVERD,
    /**
     * 0xff 不可接受方法
     */
    NO_ACCEPTABLE;

    public static AuthType valueOf(int authTypeIntValue){
      if(authTypeIntValue == 0x00)
        return NONE;
      else if (authTypeIntValue == 0x01) {
          return GSSAPI;
      }
      else if (authTypeIntValue == 0x02) {
        return USER_PWD;
      }
      else if (authTypeIntValue >= 0x03 && authTypeIntValue <= 0x7f) {
          return IANA;
      }
      else if (authTypeIntValue >= 0x80 && authTypeIntValue <= 0xfe) {
          return RESERVERD;
      }
      else {
        return NO_ACCEPTABLE;
      }
    }
  }

}