package test.thread;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by hooxin on 15-3-24.
 */
public class DynaThreadSum {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        List<Future<Long>> results =
                executorService.invokeAll(Arrays.asList(new Sum(1, 1000), new Sum(1001, 10000), new Sum(10001, 1000000)));
       executorService.shutdown();
        ///////////////合并
        long rst=0;
        for (Future<Long> result : results) {
            System.out.println(result.get());
            rst+=result.get();
        }
        System.out.println("result is "+rst);
    }


}

class Sum implements Callable<Long> {
    private final long from;
    private final long to;

    public Sum(long from, long to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public Long call() throws Exception {
        long result = 0;
        for (long i = from; i <= to; i++)
            result += i;
        return result;
    }
}