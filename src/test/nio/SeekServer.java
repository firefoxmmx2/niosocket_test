package test.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

/**
 * Created by hooxin on 14-6-23.
 */
public class SeekServer extends Thread{
    Logger logger=Logger.getLogger(SeekServer.class.getName());

    private final int ACCEPT_PORT=10086;
    private final int TIME_OUT=3600;
    private Selector mSelector=null;
    private ServerSocketChannel mSocketChannel=null;
    private ServerSocket mServerSocket=null;
    private InetSocketAddress mAddress=null;

    public SeekServer(){
        long sign=System.currentTimeMillis();
        try{
            mSocketChannel=ServerSocketChannel.open();
            if(mSocketChannel==null){
                System.out.println("cant open server socket");
            }
            mServerSocket=mSocketChannel.socket();
            mAddress=new InetSocketAddress(ACCEPT_PORT);
            mServerSocket.bind(mAddress);
            logger.info("server bind port is "+ACCEPT_PORT);
            mSelector=Selector.open();
            mSocketChannel.configureBlocking(false);
            SelectionKey key=mSocketChannel.register(mSelector,SelectionKey.OP_ACCEPT);
            key.attach(new Acceptor());
            logger.info("Seek server startup in "+(System.currentTimeMillis()-sign)+"ms!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        logger.info("server is listening... ");
        while (!Thread.interrupted()){
            try{
                if(mSelector.select(TIME_OUT)>0){
                    logger.info("find a new selection key");
                    for(SelectionKey key : mSelector.selectedKeys()){
                        Runnable at=(Runnable)key.attachment();
                        if(at!=null){
                            at.run();
                        }
//                        mSelector.selectedKeys().clear();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                logger.warning(e.getMessage());
            }
        }
    }

    class Acceptor implements Runnable{

        @Override
        public void run() {
            SocketChannel sc= null;
            try {
                sc = mSocketChannel.accept();
                new Handler(mSelector,sc);
            } catch (IOException e) {
                e.printStackTrace();
                logger.warning(e.getMessage());
            }

        }
    }

    class Handler {
        public Handler(Selector selector,SocketChannel socketChannel) {

        }
    }
}
