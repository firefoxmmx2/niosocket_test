package test.socks;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by hooxin on 14-10-4.
 */
public class SocksProxy {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(9898);
        System.out.println("socks 代理开始监听于" + serverSocket.getLocalPort());
        SocksDaemon socksProxy = new SocksDaemon(serverSocket);

//
//        ServerSocket server  = new ServerSocket(9898);
//        while(true){
//            Socket socket = server.accept();
//            ActionSocket ap = new ActionSocket(socket);
//            ap.start();
//        }
    }
}

class SocksDaemon extends Thread {
    private ServerSocket server;

    public SocksDaemon(ServerSocket server) {
        this.server = server;
        start();
    }

    @Override
    public void run() {
        Socket connection;

        while (true) {
            try {
                connection = server.accept();
                SockSServerThread handler = new SockSServerThread(connection);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}

/**
 * 这个线程用于从内网读取数据,发给外网
 */
class SockSServerThread extends Thread {
    private Socket connection;
    private static int BUFF_LEN = 10000;

    int bytes2int(byte b) { //byte转int
        int mask = 0xff;
        int temp = 0;
        int res = 0;
        res <<= 8;
        temp = b & mask;
        res |= temp;
        return res;
    }

    public SockSServerThread(Socket connection) {
        this.connection = connection;
        start();
    }

    @Override
    public void run() {
        byte[] buf = new byte[BUFF_LEN];
        byte[] buf1 = new byte[BUFF_LEN];
        byte[] buf2 = new byte[BUFF_LEN];
        int readbytes = 0;
        int readbytes1 = 0;
        int readbytes2 = 0;
        DataInputStream in = null;
        DataInputStream in1 = null;
        DataOutputStream out = null, out1 = null;
        String s = null, s1 = null, s2 = null;
        Socket client = null;
        int i;
        int port = 0, port1 = 0;
        String ip = null;
        byte[] ip1 = new byte[4], ip2 = new byte[4];
        try {
            in = new DataInputStream(connection.getInputStream());
            out = new DataOutputStream(connection.getOutputStream());
            if (in != null && out != null) {
                readbytes = in.read(buf, 0, BUFF_LEN);
                if (readbytes > 0) {  //读取数据
                    if (buf[0] == 5) { //判断是不是为socks5
//                        发送socks5应答
                        buf1[0] = 5;
                        buf1[1] = 0;
                        out.write(buf1, 0, 2);
                        out.flush();
//                        继续读取sock5请求
                        readbytes = in.read(buf, 0, BUFF_LEN);
                        if (readbytes > 0) {
                            if (buf[0] == 0x5 && buf[1] == 0x1 && buf[2] == 0x0 && buf[3] == 0x1) {
//                                从这个请求中获取连接的ip地址和端口,然后创建对应的tcp连接
                                ip = bytes2int(buf[4]) + "." + bytes2int(buf[5]) + "." + bytes2int(buf[6]) + "." + bytes2int(buf[7]);
                                port = buf[8] * 256 + buf[9];

//                                port = Integer.parseInt(bytes2int(buf[8])+""+bytes2int(buf[9]));
//                                port = 80;

                            } else {
                                s = new String(buf);
                                s = s.substring(5);
                                System.out.println(new String(s));
                                int index = s.indexOf("\0");
                                s = s.substring(0, index);
                                port = buf[5 + index] * 256 + buf[5 + index + 1];
                                ip=s;
                            }

                            client = new Socket(ip, port);
                            in1 = new DataInputStream(client.getInputStream());
                            out1 = new DataOutputStream(client.getOutputStream());
//                                发送sock5响应
                            ip1 = client.getLocalAddress().getAddress();
                            port1 = client.getLocalPort();
                            buf[1] = 0;
                            buf[4] = ip1[0];
                            buf[5] = ip1[1];
                            buf[6] = ip1[2];
                            buf[7] = ip1[3];
                            buf[8] = (byte) (port1 >> 8);
                            buf[9] = (byte) (port1 & 0xff);
                            out.write(buf, 0, 10);
                            out.flush();
//                                发送数据给客户端
                            SOCKSServerThread1 thread1 = new SOCKSServerThread1(in1, out,client);
                            while (true) {
                                try {
                                    if (readbytes1 == -1)
                                        break;
                                    readbytes1 = in.read(buf1, 0, BUFF_LEN);
                                    if (readbytes1 > 0) {
                                        out1.write(buf1, 0, readbytes1);
                                        out.flush();
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    break;
                                }
                            }
                        }
                    }
//                    if (buf[0] == 4) { //判断请求是不是为sock4
//                        port = buf[2] * 256 + buf[3]; //读取端口号
//                        if (buf[4] == 0 & buf[5] == 0 && buf[6] == 0 && buf[7] != 0
//                                && buf[8] == 0) {
////                            如果请求为域名的话
//                            s = new String(buf);
//                            s = s.substring(9);
//                            s = s.substring(0, s.indexOf("\0"));
//                        } else {
////                        如果请求为ip
//                            ip = bytes2int(buf[4]) + "." + bytes2int(buf[5]) + "." + bytes2int(buf[6]) + "." + bytes2int(buf[7]);
//                            s = ip;
//                        }
//
//                        for (i = 1; i <= 9; i++)
//                            buf[i - 1] = 0;
//                        client = new Socket(s, port);
////                    根据sock4 请求中的地址建立tcp套接字 也就是转发的套接字
//                        in1 = new DataInputStream(client.getInputStream());
//                        out1 = new DataOutputStream(client.getOutputStream());
//
////                    返回sock4应答
//                        ip1 = client.getLocalAddress().getAddress();
//                        port1 = client.getLocalPort();
//                        buf[0] = 0;
//                        buf[1] = 0x5a;
//                        buf[2] = ip1[0];
//                        buf[3] = ip1[1];
//                        buf[4] = (byte) (port1 >> 8);    //把之前转化为int的byte数据转化回byte,在写入输出流
//                        buf[5] = (byte) (port1 & 0xff);
//                        out.write(buf, 0, 8);
//                        out.flush();
//
//                        // SOCKSServerThread1
//                        SOCKSServerThread1 thread1 = new SOCKSServerThread1(in1, out,client);
//                        while (true) {
//                            if (readbytes1 == -1)
//                                break;
//                            try {
//                                readbytes1 = in.read(buf1, 0, BUFF_LEN);
//                                if (readbytes1 > 0) {
//                                    out1.write(buf1, 0, readbytes1);
//                                    out1.flush();
//                                }
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                                break;
//                            }
//                        }
//                    }
                }
            }

//            关闭流
            if (in1 != null)
                in1.close();
            if (out1 != null)
                out1.close();
            if (client != null && !client.isClosed())
                client.close();
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (connection != null)
                connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/**
 * 用于读取外网返回数据,然后转发给客户端
 */
class SOCKSServerThread1 extends Thread {
    private DataInputStream in;
    private DataOutputStream out;
    private static int BUF_LEN = 10000;
    private Socket client;
    public SOCKSServerThread1(DataInputStream in, DataOutputStream out,Socket client) {
        this.in = in;
        this.out = out;
        this.client=client;
        start();
    }

    @Override
    public void run() {
        //读取返回数据,转发给客户端
        int readbytes = 0;
        byte[] buf = new byte[BUF_LEN];
        while (true) {
            if (readbytes == -1)
                break;
            try {
                readbytes = in.read(buf, 0, BUF_LEN);
                if (readbytes > 0) {
                    out.write(buf, 0, readbytes);
                    out.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }
}

class ActionSocket extends Thread{
    private Socket socket = null ;
    public ActionSocket(Socket s){
        this.socket = s ;
    }
    public void run(){
        try{
            this.action() ;
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    public void action() throws Exception {
        if (this.socket == null){
            return ;
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
        for(String temp = br.readLine() ; temp!=null;temp = br.readLine() ){
            System.out.println(new String(temp.getBytes(),"utf8"));
        }
        br.close();
    }
}
