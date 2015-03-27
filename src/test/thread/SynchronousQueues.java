package test.thread;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

/**
 * Created by hooxin on 15-3-26.
 */
public class SynchronousQueues {
    public static void main(String[] args) {
        BlockingQueue<String> drop=new SynchronousQueue<>();
        ExecutorService executorService= Executors.newFixedThreadPool(3);
        executorService.execute(new Producer(drop));
        executorService.execute(new Consumer(drop));
        executorService.shutdown();
    }
}
