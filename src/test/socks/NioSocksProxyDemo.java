package test.socks;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NioSocksProxyDemo {
  public static void main(String[] args) throws IOException {
    NioSock5Server server=new NioSock5Server(9898);
    server.listen();
  }
}

class NioSock5Server {
  private Selector selector;
  private ServerSocketChannel serverSocketChannel;
  private ExecutorService executorService;
  private final int MAX_THREAD_NUMS = 180;

  public NioSock5Server(int port) throws IOException {
    serverSocketChannel = ServerSocketChannel.open();
    selector = Selector.open();
    System.out.println("init pool thread executors ");
    executorService = Executors.newFixedThreadPool(MAX_THREAD_NUMS);
    System.out.println(" listen at port : " + port);
    serverSocketChannel.bind(new InetSocketAddress(port));
    serverSocketChannel.configureBlocking(false);
    serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
  }

  public void listen() throws IOException {
    try {
      while (true) {
        selector.select(250);
        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
        while(keys.hasNext()){
          SelectionKey key = keys.next();
          keys.remove();
          NioSock5ServerHandler handler = new NioSock5ServerHandler(selector, key,executorService);
          handler.doJob();
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      selector.close();
      serverSocketChannel.close();
      executorService.shutdown();
    }
  }

}

class NioSock5ServerHandler {
  private Selector selector;
  private SelectionKey key;
  private ExecutorService executorService;

  public NioSock5ServerHandler(Selector selector, SelectionKey key, ExecutorService executorService) {
    this.selector = selector;
    this.key = key;
    this.executorService = executorService;
  }

  public NioSock5ServerHandler(Selector selector,SelectionKey key) {
    this.selector=selector;
    this.key =  key;
  }

  public void doJob() {
    try {
      if(key.isAcceptable()) {
        serverAccept();
      }
      else if (key.isReadable()){
        SocketChannel channel = (SocketChannel) key.channel();
        channel.register(selector,0);
        serverRead();
      }
      else if (key.isWritable()){
        SocketChannel channel= (SocketChannel) key.channel();
        channel.register(selector,0);
        serverWrite();
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
    }
  }

  public void serverAccept() throws IOException {
    AcceptServerHandler handler = new AcceptServerHandler(selector, key);
    handler.run();
//    executorService.execute(handler);
  }

  public void serverRead() {
    ReadServerHandler handler = new ReadServerHandler(selector, key);
    executorService.execute(handler);
//    handler.run();
  }

  public void serverWrite() {
    executorService.execute(new WriteServerHandler(selector,key));
  }


  class AcceptServerHandler implements Runnable{
    private Selector selector;
    private SelectionKey key;

    public AcceptServerHandler(Selector selector, SelectionKey key) {
      this.selector = selector;
      this.key = key;
    }

    @Override
    public void run() {
      System.out.println("在进程"+Thread.currentThread().getId()+"的统一接入连接状态");
      ServerSocketChannel serverChannel= (ServerSocketChannel) key.channel();
      try {
        SocketChannel channel=serverChannel.accept();
        channel.configureBlocking(false);
        SocksMessage msg=new SocksMessage();
        msg.setCurrentStep(Socks.Steps.HAND_SHAKE);
        channel.register(selector, SelectionKey.OP_READ,msg);
      } catch (IOException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }
  }

  class ReadServerHandler implements Runnable {
    private Selector selector;
    private SelectionKey key;
    private ByteBuffer buffer;
    private final int BUFFER_LEN=8*1024;
    public ReadServerHandler(Selector selector, SelectionKey key) {
      this.selector = selector;
      this.key = key;
      buffer=ByteBuffer.allocate(BUFFER_LEN);
    }

    @Override
    public void run() {
      System.out.println("在进程"+Thread.currentThread().getId()+"的读取状态");
      SocketChannel channel= (SocketChannel) key.channel();
      try {
        int readcount=channel.read(buffer);
        if(readcount == -1){
          key.cancel();
          channel.close();
        }
        Object attachment=key.attachment();
        if(attachment!=null) {
          if(attachment instanceof SocksMessage){
            SocksMessage msg= (SocksMessage) attachment;
            System.out.println("当前代理步骤位于"+msg.getCurrentStep());
            if(Socks.Steps.HAND_SHAKE == msg.getCurrentStep()){
              SocksHandShake handShake= SockHandShakeBuilder.create(buffer, selector, key, msg);
              handShake.readSocksVersion();
            }
            else if(Socks.Steps.BIND == msg.getCurrentStep()){
              SocksBind bind=SocksBindBuilder.create(buffer,selector,key,msg);
              bind.receiveProxyTargetAddressInfomation();
            }
            else if (Socks.Steps.TRANSFER == msg.getCurrentStep()){
              // todo 传输转发
            }
          }
        }

      } catch (ClosedChannelException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      } catch (IOException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }
  }

  class WriteServerHandler implements Runnable {
    private Selector selector;
    private SelectionKey key;
    private ByteBuffer buffer;
    public WriteServerHandler(Selector selector, SelectionKey key) {
      this.selector = selector;
      this.key = key;
    }

    @Override
    public void run() {
      System.out.println("在进程"+Thread.currentThread().getId()+"的写入状态");
      SocketChannel channel= (SocketChannel) key.channel();
      Object attachment = key.attachment();
      if(attachment != null && attachment instanceof SocksMessage) {
        SocksMessage msg= (SocksMessage) attachment;
        if(Socks.Steps.HAND_SHAKE ==  msg.getCurrentStep()){
          SocksHandShake handShake = SockHandShakeBuilder.create(buffer,selector,key,msg);
          handShake.sendReady();
        }
        else if (Socks.Steps.BIND == msg.getCurrentStep()){
          SocksBind bind=SocksBindBuilder.create(buffer,selector,key,msg);
          bind.sendLocalAddressInfomation();
          bind.sendReady();
        }
        else if (Socks.Steps.TRANSFER == msg.getCurrentStep()){
          // todo 传输转发
        }
      }
    }
  }

}

interface SocksHandShake {
  void readSocksVersion();
  void sendReady();
}

interface SocksBind {
  void receiveProxyTargetAddressInfomation() throws UnknownHostException;
  void sendLocalAddressInfomation();
  void sendReady();
}

interface SocksSocketData {
  void readServerDataToWriteProxyTagget();
  void readProxyTargetDataToWriteServer();
}

class SocksMessage {
  private Socks.Version ver;
  private Socks.AuthType authType;
  private Socks.AddressType addressType;
  private Socks.Steps currentStep;
  private byte[] data;
  private InetAddress proxyTargetAddress;
  private int proxyTargetPort;

  public InetAddress getProxyTargetAddress() {
    return proxyTargetAddress;
  }

  public void setProxyTargetAddress(InetAddress proxyTargetAddress) {
    this.proxyTargetAddress = proxyTargetAddress;
  }

  public int getProxyTargetPort() {
    return proxyTargetPort;
  }

  public void setProxyTargetPort(int proxyTargetPort) {
    this.proxyTargetPort = proxyTargetPort;
  }

  public byte[] getData() {
    return data;
  }

  public void setData(byte[] data) {
    this.data = data;
  }

  public Socks.Steps getCurrentStep() {
    return currentStep;
  }

  public void setCurrentStep(Socks.Steps currentStep) {
    this.currentStep = currentStep;
  }

  public Socks.Version getVer() {
    return ver;
  }

  public void setVer(Socks.Version ver) {
    this.ver = ver;
  }

  public Socks.AuthType getAuthType() {
    return authType;
  }

  public void setAuthType(Socks.AuthType authType) {
    this.authType = authType;
  }

  public Socks.AddressType getAddressType() {
    return addressType;
  }

  public void setAddressType(Socks.AddressType addressType) {
    this.addressType = addressType;
  }

}

class Socks5HandShake implements SocksHandShake {
  private ByteBuffer buffer;
  private Selector serverSelector;
  private SelectionKey key;
  private SocksMessage msg;
  public Socks5HandShake(ByteBuffer buffer, Selector serverSelector, SelectionKey key,SocksMessage msg) {
    this.buffer = buffer;
    this.serverSelector = serverSelector;
    this.key = key;
    this.msg = msg;
  }

  @Override
  public void readSocksVersion() {
    int ver = buffer.get();
    int methodNum = buffer.get();
    int authTypeInt = buffer.get(methodNum);

    msg.setAuthType(Socks.AuthType.valueOf(authTypeInt));
    msg.setVer(Socks.Version.V5);
  }

  @Override
  public void sendReady() {
    buffer.put((byte)5);
    buffer.put((byte)0);
    buffer.flip();

  }
}
class SockHandShakeBuilder {
  public static SocksHandShake create(ByteBuffer buffer, Selector selector, SelectionKey key, SocksMessage msg){
    if(buffer == null)
      throw new RuntimeException("缓冲区为空");
    if(buffer.hasRemaining()){
      throw new RuntimeException("缓冲区里面没有可用的数据");
    }
    int ver = buffer.get();
    if(ver == Socks.Version.V5.getValue()){
      buffer.rewind();
      return new Socks5HandShake(buffer,selector,key,msg);
    }
    else if(ver == Socks.Version.V4A.getValue()){
      return null;
    }
    else {
      throw new RuntimeException("不支持该种类型的SOCKS代理");
    }
  }
}
class Socks5Bind implements SocksBind {
  private ByteBuffer buffer;
  private Selector serverSelector;
  private SelectionKey key;
  private SocksMessage msg;

  public Socks5Bind(ByteBuffer buffer, Selector serverSelector, SelectionKey key,SocksMessage msg) {
    this.buffer = buffer;
    this.serverSelector = serverSelector;
    this.key = key;
    this.msg = msg;
  }

  @Override
  public void receiveProxyTargetAddressInfomation() throws UnknownHostException {
    SocketChannel channel= (SocketChannel) key.channel();
    buffer.flip();
    byte[] sockinfos=new byte[3];
    buffer.get(sockinfos);
    int ver = sockinfos[0];
    int cmd = sockinfos[1];
    if(!(Socks.CMDType.CONNECT.getValue() == cmd)) {
      throw new RuntimeException("不支持该种端口处理方式 CMD :"+cmd);
    }
    int rsv = sockinfos[2];

    int addrTypeValue = buffer.get();
    Socks.AddressType addressType = Socks.AddressType.valueOf(addrTypeValue);
    if(addressType == Socks.AddressType.IPV4){
      byte[] ipBytes=new byte[4];
      buffer.get(ipBytes);
      byte[] portBytes=new byte[2];
      buffer.get(portBytes);
      int port = portBytes[0] & 0xff << 8 + portBytes[1] & 0xff ;
      msg.setProxyTargetAddress(InetAddress.getByAddress(ipBytes));
      msg.setProxyTargetPort(port);
    }
    else if (addressType == Socks.AddressType.IPV6){
      byte[] ipBytes=new byte[6];
      buffer.get(ipBytes);
      byte[] portBytes=new byte[2];
      buffer.get(portBytes);
      int port = portBytes[0] & 0xff << 8 + portBytes[1] & 0xff ;
      msg.setProxyTargetAddress(InetAddress.getByAddress(ipBytes));
      msg.setProxyTargetPort(port);
    }
    else if (addressType == Socks.AddressType.DOMAIN) {
      int hostLength = buffer.get();
      byte[] hostBytes=new byte[hostLength];
      buffer.get(hostBytes);
      byte[] portBytes=new byte[2];
      buffer.get(portBytes);
      String hostname = new String(hostBytes);
      int port = portBytes[0] & 0xff << 8 + portBytes[1] & 0xff ;
      msg.setProxyTargetAddress(InetAddress.getByName(hostname));
      msg.setProxyTargetPort(port);
    }
    else {
      throw new RuntimeException("不支持这种地址类型");
    }

  }

  @Override
  public void sendLocalAddressInfomation() {

  }

  @Override
  public void sendReady() {

  }
}

class SocksBindBuilder {
  public static SocksBind create(ByteBuffer buffer,Selector selector,SelectionKey key,SocksMessage msg){
    if(buffer == null)
      throw new RuntimeException("缓冲区为空");
    if(buffer.hasRemaining()){
      throw new RuntimeException("缓冲区里面没有可用的数据");
    }

    int ver = buffer.get();
    if(ver == Socks.Version.V5.getValue()) {
      buffer.rewind();
      return new Socks5Bind(buffer,selector,key,msg);
    }
    else if (ver == Socks.Version.V4A.getValue()){
      buffer.rewind();
      throw new RuntimeException("暂时不支持SOCKS 4A版本代理");
    }
    else {
      throw new RuntimeException("不支持该种类的SOCKS代理");
    }
  }
}

class Socks {
  public enum Version {
    V5(0x05),
    V4A(0x04);

    private int value;

    Version(int i) {
      value = i;
    }

    public int getValue() {
      return value;
    }

    public byte byteValue() {
      return new Integer(value).byteValue();
    }
  }

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

  public enum Steps {
    HAND_SHAKE, // 握手
    BIND,   // 绑定
    TRANSFER; // 传输
  }
}
