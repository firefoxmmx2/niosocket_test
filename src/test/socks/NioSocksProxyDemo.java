package test.socks;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NioSocksProxyDemo {
  public static void main(String[] args) throws IOException {
    NSocks5Server socks5Server = new NSocks5Server(9898);
    socks5Server.listen();
//    NSocks4aServer socks4aServer=new NSocks4aServer(9898);

  }
}

class NSocks4aServer {
  class SocksClient {
    boolean connected;
    SocketChannel client, remote;
    long lastData = 0;

    SocksClient(SocketChannel c) throws IOException {
      client = c;
      client.configureBlocking(false);
      lastData = System.currentTimeMillis();
    }

    public void newRemoteData(Selector selector, SelectionKey sk) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(1024);
      if(remote.read(buf) == -1)
        throw new IOException("disconnected");
      lastData = System.currentTimeMillis();
      buf.flip();
      client.write(buf);
    }

    public void newClientData(Selector selector, SelectionKey sk) throws IOException {
      if(!connected) {
        ByteBuffer inbuf = ByteBuffer.allocate(512);
        if(client.read(inbuf)<1)
          return;
        inbuf.flip();

        // read socks header
        int ver = inbuf.get();
        if (ver != 4) {
          throw new IOException("incorrect version" + ver);
        }
        int cmd = inbuf.get();

        // check supported command
        if (cmd != 1) {
          throw new IOException("incorrect version");
        }

        final int port = inbuf.getShort();

        final byte ip[] = new byte[4];
        // fetch IP
        inbuf.get(ip);

        InetAddress remoteAddr = InetAddress.getByAddress(ip);

        while ((inbuf.get()) != 0) ; // username

        // hostname provided, not IP
        System.out.println("[debug] ip[0-3] = "+ip[0]+ip[1]+ip[2]+ip[3]);
        if (ip[0] == 0 && ip[1] == 0 && ip[2] == 0 && ip[3] != 0) { // host provided
          String host = "";
          byte b;
          while ((b = inbuf.get()) != 0) {
            host += b;
          }
          remoteAddr = InetAddress.getByName(host);
          System.out.println(host + remoteAddr);
        }

        remote = SocketChannel.open(new InetSocketAddress(remoteAddr, port));

        ByteBuffer out = ByteBuffer.allocate(20);
        out.put((byte)0);
        out.put((byte) (remote.isConnected() ? 0x5a : 0x5b));
        out.putShort((short) port);
        out.put(remoteAddr.getAddress());
        out.flip();
        client.write(out);

        if(!remote.isConnected())
          throw new IOException("connect failed");

        remote.configureBlocking(false);
        remote.register(selector, SelectionKey.OP_READ);

        connected = true;
      } else {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        if(client.read(buf) == -1)
          throw new IOException("disconnected");
        lastData = System.currentTimeMillis();
        buf.flip();
        remote.write(buf);
      }
    }
  }

  static ArrayList<SocksClient> clients = new ArrayList<SocksClient>();

  // utility function
  public SocksClient addClient(SocketChannel s) {
    SocksClient cl;
    try {
      cl = new SocksClient(s);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    clients.add(cl);
    return cl;
  }

  public NSocks4aServer(int port) throws IOException {
    ServerSocketChannel socks = ServerSocketChannel.open();
    socks.socket().bind(new InetSocketAddress(port));
    socks.configureBlocking(false);
    Selector select = Selector.open();
    socks.register(select, SelectionKey.OP_ACCEPT);

    int lastClients = clients.size();
    // select loop
    while(true) {
      select.select(1000);

      Set keys = select.selectedKeys();
      Iterator iterator = keys.iterator();
      while (iterator.hasNext()) {
        SelectionKey k = (SelectionKey) iterator.next();

        if (!k.isValid())
          continue;

        // new connection?
        if (k.isAcceptable() && k.channel() == socks) {
          // server socket
          SocketChannel csock = socks.accept();
          if (csock == null)
            continue;
          addClient(csock);
          csock.register(select, SelectionKey.OP_READ);
        } else if (k.isReadable()) {
          // new data on a client/remote socket
          for (int i = 0; i < clients.size(); i++) {
            SocksClient cl = clients.get(i);
            try {
              if (k.channel() == cl.client) // from client (e.g. socks client)
                cl.newClientData(select, k);
              else if (k.channel() == cl.remote) {  // from server client is connected to (e.g. website)
                cl.newRemoteData(select, k);
              }
            } catch (IOException e) { // error occurred - remove client
              cl.client.close();
              if (cl.remote != null)
                cl.remote.close();
              k.cancel();
              clients.remove(cl);
            }

          }
        }
      }

      // client timeout check
      for (int i = 0; i < clients.size(); i++) {
        SocksClient cl = clients.get(i);
        if((System.currentTimeMillis() - cl.lastData) > 30000L) {
          cl.client.close();
          if(cl.remote != null)
            cl.remote.close();
          clients.remove(cl);
        }
      }
      if(clients.size() != lastClients) {
        System.out.println(clients.size());
        lastClients = clients.size();
      }
    }
  }
}
class NSocks5Server {
  private Selector serverSelector;
  private ServerSocketChannel serverSocketChannel;
  private final static int BUFFER_LENGTH = 8 * 1024;
  private ExecutorService executorService;

  private enum Step {
    RECEIVE(new byte[]{0x05, 0x01, 0x00, 0x00}),
    SEND(new byte[]{0x05, 0x00}),
    BIND(new byte[]{0x05, 0x01, 0x00, 0x01}),
    CONNECTED(new byte[]{0x05, 0x00, 0x00, 0x03});

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
      executorService = Executors.newFixedThreadPool(100);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void listen() throws IOException {
    try {
      while (true) {
        serverSelector.select(1000);
        Iterator<SelectionKey> keys = serverSelector.selectedKeys().iterator();
        while (keys.hasNext()) {
          SelectionKey key = keys.next();
          keys.remove();
          NioSocks5ServerHandler handler = new NioSocks5ServerHandler(key);
          handler.run();
//          executorService.submit(handler);

        }

      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      serverSelector.close();
      serverSocketChannel.socket().close();
      serverSocketChannel.close();
      executorService.shutdown();
    }
  }

  class NioSocks5ServerHandler implements Runnable {
    private SelectionKey key;
    private ByteBuffer buffer;

    public NioSocks5ServerHandler(SelectionKey key) {
      this.key = key;
      buffer = ByteBuffer.allocate(BUFFER_LENGTH);
    }

    @Override
    public void run() {
      if (key.isConnectable()) {
        SocketChannel channel = (SocketChannel) key.channel();
        try {
          channel.finishConnect();
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else if (key.isAcceptable()) {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        try {
          SocketChannel channel = serverChannel.accept();
          channel.configureBlocking(false);
          SocksMessage socksMessage = new SocksMessage();
          channel.register(serverSelector, SelectionKey.OP_READ, socksMessage);
//          key.attach(socksMessage);
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else if (key.isReadable()) {
        SocketChannel channel = (SocketChannel) key.channel();
        SocksMessage socksMessage = (SocksMessage) key.attachment();
        System.out.println("在读模式下:");
        try {
          int readcount = 0;
          if ((readcount = channel.read(buffer)) > 0) {
            buffer.flip();
//            System.out.println("读取缓冲里面的信息:"+buffer.limit());
            if (!socksMessage.isConnected()) {
              System.out.println("read  isconnected is false");
              if (socksMessage.getStatus() == Step.RECEIVE) {
                System.out.println("读取SOCKS请求信息");
                int ver = buffer.get();
                int authTypeMethodLength = buffer.get();
                byte[] authTypeIntValueBytes = new byte[authTypeMethodLength];
                buffer.get(authTypeIntValueBytes);
                Socks.AuthType authType = Socks.AuthType.valueOf(authTypeIntValueBytes[0]);
                socksMessage.setVer(Socks.Vers.valueOf(ver));
                socksMessage.setAuthType(authType);
                if (channel.isConnected())
                  channel.register(serverSelector, SelectionKey.OP_WRITE, socksMessage);
              } else if (socksMessage.getStatus() == Step.SEND) {
                System.out.println("读取SOCKS验证请求");
                socksMessage.setData(buffer.array());
                if (channel.isConnected())
                  channel.register(serverSelector, SelectionKey.OP_WRITE, socksMessage);
              } else if (socksMessage.getStatus() == Step.BIND) {
                System.out.println("读取SOCKS绑定请求");
                if (channel.isConnected())
                  channel.register(serverSelector, SelectionKey.OP_WRITE, socksMessage);
              }
            }
            buffer.clear();
          } else {
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
        SocksMessage socksMessage = null;
        System.out.println("在写模式下:");
        Object attach = key.attachment();
        if (attach != null && attach instanceof SocksMessage)
          socksMessage = (SocksMessage) attach;
        if (channel.isConnected() && socksMessage != null) {
          System.out.println("[debug]socksMessage.getStatus() = " + socksMessage.getStatus());
          if (!socksMessage.isConnected()) {
            if (socksMessage.getStatus() == Step.RECEIVE) {
              try {
                System.out.println("响应SOCKS验证回复");
//                ByteBuffer buffer=ByteBuffer.allocate(2);
                buffer.put(socksMessage.getVer().getValue().byteValue());
                if (socksMessage.getAuthType() == Socks.AuthType.NONE
                    || socksMessage.getAuthType() == Socks.AuthType.NO_ACCEPTABLE) {
                  System.out.println("未使用验证");
                  buffer.put((byte) 0x00);
                } else if (socksMessage.getAuthType() == Socks.AuthType.USER_PWD) {
                  // TODO 用户验证
                  System.out.println("用户验证");
                } else if (socksMessage.getAuthType() == Socks.AuthType.GSSAPI) {
                  // TODO 通用安全服务应用程序接口 验证
                  System.out.println("通用安全服务应用程序接口验证");
                } else if (socksMessage.getAuthType() == Socks.AuthType.IANA) {
                  // TODO IANA 分配认证
                  System.out.println("IANA 分配认证");
                }

                buffer.flip();
                channel.write(buffer);
                socksMessage.setStatus(Step.SEND);
//                System.out.println("[debug]socksMessage.getStatus() = " + socksMessage.getStatus());
                if (channel.isConnected())
                  channel.register(serverSelector, SelectionKey.OP_READ, socksMessage);
              } catch (IOException e) {
                e.printStackTrace();
              }
            } else if (socksMessage.getStatus() == Step.SEND) {
              System.out.println("响应SOCKS发送步骤回复");
              byte[] buf = socksMessage.getData();
              String ip = "";
              int port = 0;
              buffer.clear();
              buffer.put(Step.BIND.getValue());
              if ((buf[0] == Step.BIND.getValue()[0] && buf[1] == Step.BIND.getValue()[1] && buf[2] == Step.BIND.getValue()[2] && buf[3] == Step.BIND.getValue()[3])) {
                ip = (bytes2int(buf[4])) + "." + (bytes2int(buf[5])) + "." + (bytes2int(buf[6])) + "." + (bytes2int(buf[7]));
                port = buf[8] * 256 + buf[9];
              } else {
                ip = new String(buf);
                int endIdx = ip.indexOf("\0", 5);
                ip = ip.substring(5, endIdx);
                port = buf[endIdx] * 256 + buf[endIdx + 1];
              }
              try {
                buffer.put(ip.getBytes());
                buffer.put((byte)port);
                buffer.flip();
                channel.write(buffer);
                buffer.clear();
//                buffer.put(buf);
//                buffer.flip();
//                channel.write(buffer);
                socksMessage.setData(null);
                socksMessage.setStatus(Step.BIND);
                channel.register(serverSelector, SelectionKey.OP_READ, socksMessage);
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
    private Step status = Step.RECEIVE;
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
      value = i;
    }

    public Integer getValue() {
      return value;
    }

    public static Vers valueOf(int verIntValue) {
      Vers ver = null;
      switch (verIntValue) {
        case 0x05:
          ver = V5;
          break;
        case 0x04:
          ver = V4a;
          break;
        default:
          ver = null;
          break;
      }
      return ver;
    }
  }

  ;

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

    public static AuthType valueOf(int authTypeIntValue) {
      if (authTypeIntValue == 0x00)
        return NONE;
      else if (authTypeIntValue == 0x01) {
        return GSSAPI;
      } else if (authTypeIntValue == 0x02) {
        return USER_PWD;
      } else if (authTypeIntValue >= 0x03 && authTypeIntValue <= 0x7f) {
        return IANA;
      } else if (authTypeIntValue >= 0x80 && authTypeIntValue <= 0xfe) {
        return RESERVERD;
      } else {
        return NO_ACCEPTABLE;
      }
    }
  }

}