package test.socks;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HttpProxyDemo {
}
class HttpDaemon extends Thread {
  private ServerSocket server;
  public HttpDaemon(ServerSocket server){
    this.server = server;
    start();
  }

  @Override
  public void run() {
    Socket connection;
    while(true){
      try {
        connection=server.accept();
        HttpProxyServer handler = new HttpProxyServer(connection);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}

class HttpProxyServer extends Thread {
  private Socket connection;
  private final int BUF_LEN=10000;
  public HttpProxyServer(Socket connection) {
    this.connection=connection;
  }
  @Override
  public void run() {
    byte[] buf=new byte[BUF_LEN];
    byte[] buf1=new byte[BUF_LEN];
    byte[] bu2=new byte[BUF_LEN];
    int readbytes=0,readbytes1=0;
    String s=null,s1=null,s2=null;
    Socket client=null;
    int port=80;
    DataInputStream in=null,in1=null;
    DataOutputStream out=null,out1=null;
    int method=0;

    try {
      in=new DataInputStream(connection.getInputStream());
      out=new DataOutputStream(connection.getOutputStream());
      if(in !=null &&out!=null){
        readbytes=in.read(buf,0,BUF_LEN);
        if(readbytes>0){
          s=new String(buf);
          if(s.indexOf("\r\n")!=-1)
            s=s.substring(0,s.indexOf("\r\n"));
          if(s.indexOf("GET")!=-1) method=0;
          if(s.indexOf("CONNECT")!=-1){
            s1=s.substring(s.indexOf("CONNECT")+8,s.indexOf("HTTP/"));
            s2=s1;
            s1=s2.substring(0,s1.indexOf(":"));
            s2=s2.substring(s2.indexOf(":")+1) ;
            s2=s2.substring(0,s2.indexOf(" "));
            port=Integer.parseInt(s2);
            method=1;
            s2="HTTP/1.1 200 Connection established\r\n";
            s2=s2+"Proxy-agent: proxy\r\n\r\n";
            buf2=s2.getBytes();
            out.write(buf);
            out.flush();

          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

