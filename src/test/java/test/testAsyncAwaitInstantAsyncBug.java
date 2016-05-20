package test;

import org.junit.Assert;
import org.junit.Test;

import static lab.mars.util.async.AsyncStream.instantAsync;

/**
 * Created by haixiao on 2015/6/24.
 * Email: wumo@outlook.com
 */
public class testAsyncAwaitInstantAsyncBug {

    @Test
    public void testWhenAsync() {
        boolean[] touch = new boolean[]{false};
        instantAsync().when(instantAsync()).then(() -> touch[0] = true).end();
        Assert.assertTrue(touch[0] == true);
    }

    @Test
    public void testAsync() {
        boolean[] touch = new boolean[]{false};
        instantAsync().then(() -> instantAsync()).then(() -> touch[0] = true).end();
        Assert.assertTrue(touch[0] == true);
    }
}
