package test;

import lab.mars.util.async.AsyncStream;
import org.junit.Assert;
import org.junit.Test;

import static lab.mars.util.async.AsyncStream.deferredAsync;
import static lab.mars.util.async.AsyncStream.instantAsync;

/**
 * Created by haixiao on 2015/3/21.
 * Email: wumo@outlook.com
 */
public class TestAsync {
    private int loop = 3;

    //FIX design testcase for multi asyncstream switch at the same time
    @Test
    public void test() {
        AsyncStream async = AsyncStream.deferredAsync();
        int[] recv = new int[5];
        async.<Integer>then(e -> {
            recv[0] = e;
        }).<Integer>then(e -> {
            recv[1] = e;
        }).<Integer>loop(e -> {
            recv[5 - loop] = e;
            return --loop > 0;
        }).end();

        async.onEvent(2);
        Assert.assertTrue(!async.isEnd());
        Assert.assertTrue(recv[0] == 2);
        async.onEvent(3);
        Assert.assertTrue(!async.isEnd());
        Assert.assertTrue(recv[1] == 3);
        async.onEvent(4);
        Assert.assertTrue(!async.isEnd());
        Assert.assertTrue(recv[2] == 4);
        async.onEvent(5);
        Assert.assertTrue(!async.isEnd());
        Assert.assertTrue(recv[3] == 5);
        async.onEvent(6);
        Assert.assertTrue(async.isEnd());
        Assert.assertTrue(recv[4] == 6);
        async.onEvent(7);
        Assert.assertTrue(async.isEnd());
    }

    @Test
    public void testDeferredAsync() {
        AsyncStream async = AsyncStream.deferredAsync();
        async.then(() -> {

        }).end();
        Assert.assertTrue(!async.isEnd());
    }

    @Test
    public void testNotEnd() {
        AsyncStream async = instantAsync(1);
        int[] recv = new int[1];
        async.<Integer>then(e -> {
            System.out.println(e);
        }).then(() -> {
            recv[0] = 1;
        });
        Assert.assertTrue(!async.isEnd());
        Assert.assertTrue(recv[0] == 1);
    }

    @Test
    public void testEnd() {
        AsyncStream async = instantAsync(1);
        int[] recv = new int[1];
        async.<Integer>then(e -> {
            System.out.println(e);
        }).then(() -> {
            recv[0] = 1;
        }).end();
        Assert.assertTrue(async.isEnd());
        Assert.assertTrue(recv[0] == 1);
    }

    @Test
    public void testProvidedEvents() {
        AsyncStream async = instantAsync(2, 3, 4, 5, 6, 7, 8);
        async.<Integer>then(e -> {
            System.out.println("first then recv=" + e);
        }).<Integer>then(e -> {
            System.out.println("second then revc=" + e);
        }).loop(e -> {
            System.out.println("third loop recv=" + e + " loop=" + loop);
            return --loop > 0;
        }).end();
        System.out.println("async.isEnd=" + async.isEnd());
        Assert.assertTrue(async.isEnd());
    }

    @Test
    public void testProvidedEvents2() {
        AsyncStream async = instantAsync(2, 3, 4);
        int[] recv = new int[7];
        async.then((Integer e) -> {
            recv[0] = e;
        }).then((Integer e) -> {
            recv[1] = e;
        }).loop((Integer e) -> {
            recv[5 - loop] = e;
            return --loop > 0;
        }).end();
        Assert.assertTrue(!async.isEnd());
        Assert.assertTrue(recv[0] == 2);
        Assert.assertTrue(recv[1] == 3);
        Assert.assertTrue(recv[2] == 4);
        async.onEvent(5);
        Assert.assertTrue(!async.isEnd());
        Assert.assertTrue(recv[3] == 5);
        async.onEvent(6);
        Assert.assertTrue(async.isEnd());
        Assert.assertTrue(recv[4] == 6);
        async.onEvent(7);
        Assert.assertTrue(async.isEnd());
        System.out.println("async.isEnd=" + async.isEnd());
    }

    @Test
    public void testAsyncAndAsync() {
        AsyncStream async = AsyncStream.deferredAsync();
        AsyncStream async2 = AsyncStream.deferredAsync();
        int[] recv = new int[]{0, 0, 0};
        async
                .<Integer>then(e -> {
                    recv[0] = e;
                }).then(() -> async2.then(e -> e).end())
                  .then(() -> recv[2] = 5)
                .<Integer>then(e -> {
                    recv[1] = e;
                })
                .end();
        Assert.assertTrue(recv[0] == 0);
        Assert.assertTrue(recv[1] == 0);
        Assert.assertTrue(async.chainClosed());
        async.onEvent();
        Assert.assertTrue(recv[0] == 0);
        Assert.assertTrue(recv[1] == 0);
        Assert.assertTrue(!async2.chainClosed());
        async.onEvent(5);
        Assert.assertTrue(recv[0] == 5);
        Assert.assertTrue(recv[1] == 0);
        Assert.assertTrue(recv[2] == 0);
        Assert.assertTrue(async2.chainClosed());
        Assert.assertTrue(!async2.isEnd());
        async2.onEvent(5);
        Assert.assertTrue(recv[1] == 5);
        Assert.assertTrue(async2.isEnd());

    }

    @Test
    public void testReturnResult() {
        AsyncStream async = AsyncStream.deferredAsync();
        async.<Double, Integer>then(e -> 1.0 * e)
                .<Double>then(result -> {
                    System.out.println(result);
                    Assert.assertEquals(100.0, result, 1e-9);
                });
        async.onEvent(100);
    }

    @Test
    public void testAsyncInstantAction() {
        AsyncStream async = AsyncStream.deferredAsync();
        async.then(e -> {
            return instantAsync(e).end();
        }).then(e -> {
            Assert.assertEquals(2, e);
        }).end();
        async.onEvent(2);
        Assert.assertTrue(async.isEnd());
    }

    @Test
    public void testNull() {
        AsyncStream async = AsyncStream.deferredAsync();
        async.then(e -> {
            return instantAsync(e).end();
        }).then(e -> {
            Assert.assertEquals(null, e);
        }).end();
        async.onEvent(null);
        Assert.assertTrue(async.isEnd());
    }

    @Test
    public void testWhen() {
        int[] result = new int[4];
        AsyncStream async0 = instantAsync();
        AsyncStream async1 = AsyncStream.deferredAsync().<Integer>then(e -> {result[1] = e;}).end();
        AsyncStream async2 = AsyncStream.deferredAsync().<Integer>then(e -> {result[2] = e;}).end();
        AsyncStream async3 = AsyncStream.deferredAsync().<Integer>then(e -> {result[3] = e;}).end();

        async0.when(async1, async2, async3)
              .then(() -> result[0] = 1).end();
        Assert.assertTrue(result[0] != 1);
        async1.onEvent(2);
        Assert.assertTrue(result[0] != 1);
        Assert.assertTrue(result[1] == 2);
        async2.onEvent(3);
        Assert.assertTrue(result[0] != 1);
        Assert.assertTrue(result[2] == 3);
        async3.onEvent(4);
        Assert.assertTrue(result[0] == 1);
        Assert.assertTrue(result[3] == 4);
    }

    @Test
    public void testWhenDeferredThen() {
        AsyncStream async0 = instantAsync();
        AsyncStream async1 = AsyncStream.deferredAsync().end();
        int[] result = {0};
        async0.when(async1).then(() -> {
            result[0] = 1;
        });
        Assert.assertTrue(result[0] != 1);
        async1.onEvent(1);
        Assert.assertTrue(result[0] == 1);
    }

    @Test
    public void testCollect() {
        AsyncStream async0 = instantAsync();
        AsyncStream async1 = AsyncStream.deferredAsync().then(e -> e).end();
        AsyncStream async2 = AsyncStream.deferredAsync().then(e -> e).end();
        AsyncStream async3 = AsyncStream.deferredAsync().then(e -> e).end();
        boolean[] recv = new boolean[]{false};
        async0.collect(async1, async2, async3)
                .<Object[]>then(result -> {
                    recv[0] = true;
                    for (Object o : result)
                        System.out.println(o);
                }).end();
        Assert.assertTrue(async0.chainClosed());
        Assert.assertTrue(async1.chainClosed());
        Assert.assertTrue(async2.chainClosed());
        Assert.assertTrue(async3.chainClosed());
        Assert.assertTrue(recv[0] == false);
        async1.onEvent(2);
        Assert.assertTrue(recv[0] == false);
        Assert.assertTrue(async1.isEnd());
        Assert.assertTrue(!async2.isEnd());
        Assert.assertTrue(!async3.isEnd());
        async2.onEvent(3);
        Assert.assertTrue(recv[0] == false);
        Assert.assertTrue(async2.isEnd());
        Assert.assertTrue(!async3.isEnd());
        async3.onEvent(4);
        Assert.assertTrue(recv[0] == true);
        Assert.assertTrue(async3.isEnd());
    }

    @Test
    public void testException() {
        AsyncStream async0 = deferredAsync();
        Throwable[] e1 = new Exception[]{null};
        async0.then(() -> {
            return 1 / 0;
        }).end().exception(e -> e1[0] = e);
        async0.onEvent();
        Assert.assertTrue(e1[0] instanceof ArithmeticException);
    }
}
