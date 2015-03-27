package test.thread;

import java.util.concurrent.CountDownLatch;

/**
 * Created by hooxin on 15-3-27.
 */
public class CountDownLatchs {
    private static final int N=10;
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch doneSignal=new CountDownLatch(N);
        CountDownLatch startSignal=new CountDownLatch(1);

        for (int i=1;i<=N;i++)
            new Thread(new Worker(doneSignal,startSignal,i)).start();
        System.out.println("begin");
        startSignal.countDown();
        doneSignal.await();
        System.out.println("Ok");
    }
    static class Worker implements Runnable {
        private final CountDownLatch downSignal;
        private final CountDownLatch startSignal;
        private int beginIndex;

        public Worker(CountDownLatch downSignal, CountDownLatch startSignal, int beginIndex) {
            this.downSignal = downSignal;
            this.startSignal = startSignal;
            this.beginIndex = beginIndex;
        }

        @Override
        public void run() {
            try {
                startSignal.await();//等待开始执行的信号的发布
                beginIndex=(beginIndex-1)*10+1;
                for (int i=beginIndex;i<=beginIndex+10;i++)
                    System.out.println(i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                downSignal.countDown();
            }

        }
    }
}
