package test.socks;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
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
      if (remote.read(buf) == -1)
        throw new IOException("disconnected");
      lastData = System.currentTimeMillis();
      buf.flip();
      client.write(buf);
    }

    public void newClientData(Selector selector, SelectionKey sk) throws IOException {
      if (!connected) {
        ByteBuffer inbuf = ByteBuffer.allocate(512);
        if (client.read(inbuf) < 1)
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
        System.out.println("[debug] ip[0-3] = " + ip[0] + ip[1] + ip[2] + ip[3]);
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
        out.put((byte) 0);
        out.put((byte) (remote.isConnected() ? 0x5a : 0x5b));
        out.putShort((short) port);
        out.put(remoteAddr.getAddress());
        out.flip();
        client.write(out);

        if (!remote.isConnected())
          throw new IOException("connect failed");

        remote.configureBlocking(false);
        remote.register(selector, SelectionKey.OP_READ);

        connected = true;
      } else {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        if (client.read(buf) == -1)
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
    while (true) {
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
        if ((System.currentTimeMillis() - cl.lastData) > 30000L) {
          cl.client.close();
          if (cl.remote != null)
            cl.remote.close();
          clients.remove(cl);
        }
      }
      if (clients.size() != lastClients) {
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
                channel.register(serverSelector, SelectionKey.OP_WRITE, socksMessage);
              }
            } else {
//              验证结束后,正常的转发数据
              if (socksMessage.getStatus() == Step.SEND) {
                System.out.println("读取SOCKS响应地址信息");
                socksMessage.setData(buffer.array());
                channel.register(serverSelector, SelectionKey.OP_WRITE, socksMessage);
              }
              else if(socksMessage.getStatus() == Step.BIND){
                System.out.println("读取来自代理客户端数据");
                socksMessage.setData(buffer.array());
                channel.register(serverSelector,SelectionKey.OP_WRITE,socksMessage);
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
                socksMessage.setIsConnected(true);
                socksMessage.setStatus(Step.SEND);
//                System.out.println("[debug]socksMessage.getStatus() = " + socksMessage.getStatus());
                if (channel.isConnected())
                  channel.register(serverSelector, SelectionKey.OP_READ, socksMessage);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          } else {
            if (socksMessage.getStatus() == Step.SEND) {
              System.out.println("响应SOCKS发送步骤回复");
              byte[] buf = socksMessage.getData();
              ByteBuffer bb = ByteBuffer.wrap(buf);
              String ip = "";
              int responseCode = Socks.ResponseType.SUCCESS.getValue();
              buffer.clear();
              //sock5
              int ver = bb.get();
              int rsv=0;
              if (ver == socksMessage.getVer().getValue().byteValue()) {
                int cmd = bb.get();
                rsv = bb.get();

                if (Socks.CMDType.CONNECT.getValue() == cmd) {
                  //TCP连接方式
                  responseCode= Socks.ResponseType.SUCCESS.getValue();
                } else if (Socks.CMDType.BIND.getValue() == cmd) {
                  // todo 以后实现bind方式
                  responseCode= Socks.ResponseType.CMD_NOT_SUPPORT.getValue();
                  throw new RuntimeException("不支持该种方式");
                } else if (Socks.CMDType.UDP.getValue() == cmd) {
                  // todo 以后实现UDP方式
                  responseCode= Socks.ResponseType.CMD_NOT_SUPPORT.getValue();
                  throw new RuntimeException("不支持该种方式");
                }

                int addressTypeInt = bb.get();
                socksMessage.setAddressType(Socks.AddressType.valueOf(addressTypeInt));

                try {
                  if (socksMessage.addressType == Socks.AddressType.IPV4) {
                    //处理IPV4的地址
                    byte[] ipbytes = new byte[4];
                    bb.get(ipbytes);
                    InetAddress address = null;
                    address = InetAddress.getByAddress(ipbytes);
                    socksMessage.setAddress(address);

                  } else if (socksMessage.addressType == Socks.AddressType.IPV6) {
                    //处理IPV6 的地址
                    byte[] ipbytes = new byte[6];
                    InetAddress address = null;
                    address = InetAddress.getByAddress(ipbytes);
                    socksMessage.setAddress(address);
                  } else if (socksMessage.addressType == Socks.AddressType.DOMAIN) {
                    // 处理域名类型的地址
                    int hostLength = bb.get();
                    byte[] hostbytes = new byte[hostLength];
                    bb.get(hostbytes);
                    InetAddress address = null;
                    address = InetAddress.getByName(new String(hostbytes));
                    socksMessage.setAddress(address);
                  }
                  else{
                    responseCode= Socks.ResponseType.ADDRTYPE_NOT_SUPPORT.getValue();
                    throw new RuntimeException("不支持的地址类型");
                  }
                } catch (UnknownHostException e) {
                  throw new RuntimeException(e);
                }

                // 端口
                byte[] portbytes = new byte[2];
                bb.get(portbytes);
                socksMessage.setPortBytes(portbytes);
                int port = portbytes[0] * 256 + portbytes[1];
                socksMessage.setPort(port);
              }
              // todo sock4a

              try {
                buffer.put(socksMessage.getVer().getValue().byteValue());
                buffer.put(socksMessage.getAddress().getAddress());
                buffer.put(socksMessage.getPortBytes());
                buffer.flip();
                channel.write(buffer);
                buffer.clear();
                //建立和客户端连接的通道
                NioSocks5Client client=new NioSocks5Client(channel,socksMessage.getAddress(),socksMessage.getPort());
                socksMessage.setClient(client);
                int localPort=client.getLocalPort();
                //发送完毕响应
                buffer.put(socksMessage.getVer().getValue().byteValue());
                buffer.put((byte) responseCode);
                buffer.put((byte) rsv);
                buffer.put((byte) 0x01);
                //返回服务器自身 地址和端口
                buffer.put(InetAddress.getLocalHost().getAddress());
                buffer.putInt(localPort);
                buffer.flip();
                channel.write(buffer);
                socksMessage.setData(null);
                socksMessage.setStatus(Step.BIND);
                channel.register(serverSelector, SelectionKey.OP_READ, socksMessage);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
            else if (socksMessage.getStatus() == Step.BIND) {
              // todo 把服务器的信息转发到客户端
              ByteBuffer bb = ByteBuffer.wrap(socksMessage.getData());
              if (socksMessage.client != null) {
                NioSocks5Client client = socksMessage.getClient();
                bb.flip();
                try {
                  client.write(bb);
                } catch (IOException e) {
                  e.printStackTrace();
                  throw new RuntimeException(e);
                }
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
  }

  class NioSocks5Client implements Runnable {
    private SocketChannel clientChannel;
    private SocketChannel serverChannel;
    private Selector selector;
    private ByteBuffer buffer;
    private int BUFFER_LENGTH=8*1024;

    public NioSocks5Client(SocketChannel serverChannel,InetAddress address,int port) throws IOException {
      this.serverChannel = serverChannel;
      clientChannel = SocketChannel.open();
      clientChannel.configureBlocking(false);
      clientChannel.connect(new InetSocketAddress(address, port));
      selector = Selector.open();
      clientChannel.register(selector, SelectionKey.OP_CONNECT);
      buffer=ByteBuffer.allocate(BUFFER_LENGTH);
      run();
    }

    public int getLocalPort() throws IOException {
      return ((InetSocketAddress) clientChannel.getLocalAddress()).getPort();
    }
    public InetAddress getLocalAddress() throws IOException {
      return ((InetSocketAddress) clientChannel.getLocalAddress()).getAddress();
    }

    public int write(ByteBuffer buffer) throws IOException {
      return clientChannel.write(buffer);
    }
    @Override
    public void run() {
      try {
        while(true){
          selector.select(200);
          Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
          while(iterator.hasNext()){
            SelectionKey key=iterator.next();
            iterator.remove();
            handle(key);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        try {
          selector.close();
          clientChannel.close();
        } catch (IOException e) {
          e.printStackTrace();
          throw new RuntimeException(e);
        }
      }
    }

    private void handle(SelectionKey key) throws IOException {
      if(key.isConnectable()){
        SocketChannel channel= (SocketChannel) key.channel();
        if(channel.isConnectionPending())
          channel.finishConnect();
        channel.register(selector,SelectionKey.OP_READ);
      }
      else if(key.isReadable()){
        // todo 读取来自服务器的内容
        SocketChannel channel= (SocketChannel) key.channel();
        int readCount=channel.read(buffer);
        serverChannel.write(ByteBuffer.allocateDirect((byte)0));
        if(readCount>0) {
          buffer.flip();
          serverChannel.write(buffer);
          buffer.clear();
          channel.register(selector,SelectionKey.OP_WRITE);
        }
        else{
          key.cancel();
          channel.close();
        }

      }
      else if (key.isWritable()){
        // todo 发送数据到服务器
        SocketChannel channel= (SocketChannel) key.channel();
        int readCount=serverChannel.read(buffer);
        if(readCount>0){
          buffer.flip();
          channel.write(buffer);
          buffer.clear();
          channel.register(selector,SelectionKey.OP_READ);
        }
        else{
          key.cancel();
          channel.close();
        }
      }
    }
  }

  class SocksMessage {
    private boolean isConnected;
    private Step status = Step.RECEIVE;
    private byte[] data;
    private Socks.Vers ver;
    private Socks.AddressType addressType;
    private Socks.AuthType authType;
    private InetAddress address;
    private int port;
    private byte[] portBytes;
    private NioSocks5Client client;

    public NioSocks5Client getClient() {
      return client;
    }

    public void setClient(NioSocks5Client client) {
      this.client = client;
    }

    public InetAddress getAddress() {
      return address;
    }

    public void setAddress(InetAddress address) {
      this.address = address;
    }

    public int getPort() {
      return port;
    }

    public byte[] getPortBytes() {
      if(port!=0){
        portBytes=new byte[2];
        portBytes[0]= (byte) (port>>8);
        portBytes[1]= (byte) (port - port>>8);
      }

      return portBytes;
    }

    public void setPortBytes(byte[] portBytes) {
      this.portBytes = portBytes;
    }

    public void setPort(int port) {
      this.port = port;
    }

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

    public static AddressType valueOf(int i) {
      AddressType result = null;
      switch (i) {
        case 1:
          result = IPV4;
          break;
        case 4:
          result = IPV6;
          break;
        case 3:
          result = DOMAIN;
          break;
      }
      return result;
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

  public enum CMDType {
    CONNECT(1),
    BIND(2),
    UDP(3);

    private int value;

    CMDType(int i) {
      value = i;
    }

    public int getValue() {
      return value;
    }

  }

  public enum ResponseType {
    SUCCESS(0X00),
    SOCKS_GENERAL_FAILD(0x01),
    CONNECT_NOT_ALLOW(0X02),
    NETWORK_UNREACHABLE(0x03),
    HOST_UNREACHABLE(0X04),
    CONNECT_REFUSE(0X05),
    TTL_EXPIRE(0X06),
    CMD_NOT_SUPPORT(0X07),
    ADDRTYPE_NOT_SUPPORT(0X08);

    private int value;

    ResponseType(int i) {
      value = i;
    }

    public int getValue() {
      return value;
    }
  }
}