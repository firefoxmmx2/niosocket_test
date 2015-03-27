package test.thread;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by hooxin on 15-3-26.
 */
public class ArrayBlockingQueue {
    public static void main(String[] args) {
        BlockingQueue<String> drop=new java.util.concurrent.ArrayBlockingQueue<String>(1,true);
        ExecutorService executorService= Executors.newFixedThreadPool(2);
        executorService.execute(new Producer(drop));
        executorService.execute(new Consumer(drop));

        executorService.shutdown();
    }
}

class Producer implements Runnable {
    private BlockingQueue<String> drop;
    List<String> messages= Arrays.asList(
            "Mares eat oats",
            "Does eat oats",
            "Little lambs eat ivy",
            "Wouldn't you eat ivy too?"
            );

    public Producer(BlockingQueue<String> drop) {
        this.drop = drop;
    }

    @Override
    public void run() {
        try {
            for (String message : messages) {
                drop.put(message);
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted! "+
            "Last one out, turn out the lights!");
//            e.printStackTrace();
        }
    }
}

class Consumer implements Runnable{
    private BlockingQueue<String> drop;

    public Consumer(BlockingQueue<String> drop) {
        this.drop = drop;
    }

    @Override
    public void run() {
        try {
            String msg=null;
            while(!((msg=drop.take()).equals("DONE")))
                System.out.println(msg);
        } catch (InterruptedException e) {
//            e.printStackTrace();
            System.out.println("Interrupted! "+
                    "Last one out, turn out the lights!");
        }
    }
}