package test;

import lab.mars.util.async.AsyncStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Created by haixiao on 2015/7/2.
 * Email: wumo@outlook.com
 */
public class TestAsyncMultiThreaded {
    public static final int NCPU = Runtime.getRuntime().availableProcessors();
    private ExecutorService executor;

    @Before
    public void setup() {
        executor = new ForkJoinPool(NCPU, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
    }

    @After
    public void teardown() {
        executor.shutdownNow();
    }

    @Test
    public void basicTest() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(5);
        int loop[] = new int[]{3};
        AsyncStream async = AsyncStream.deferredAsync();
        Set<Integer> results = new LinkedHashSet<>();
        async
                .<Integer>then(e -> {
                    System.out.println("then:" + e);
                    results.add(e);
                })
                .<Integer>then(e -> {
                    System.out.println("then:" + e);
                    results.add(e);
                })
                .<Integer>loop(e -> {
                    System.out.println("loop:" + e);
                    results.add(e);
                    return --loop[0] > 0;
                })
                .end(() -> results.forEach(System.out::println));
        for (int i = 0; i < 5; i++) {
            int _i = i;
            executor.submit(() -> {
                async.onEvent(_i);
                latch.countDown();
            });
        }
        latch.await();
    }

    @Test
    public void checkQueue() throws InterruptedException {
        int nThreads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        AsyncStream async = AsyncStream.deferredAsync();
        long start = System.nanoTime();
        long due = 10000000000L;
        for (int count = 0; count < 10000; count++) {
            CountDownLatch latch = new CountDownLatch(nThreads);
            for (int i = 0; i < nThreads; i++)
                executor.submit(() -> {
                    while ((System.nanoTime() - start) < due) {
                        if (ThreadLocalRandom.current().nextBoolean()) {
                            async.onEvent(1);
                        } else {
                            async.then(e -> {});
                        }
                    }
                    latch.countDown();
                });

            latch.await();
        }

    }
}
