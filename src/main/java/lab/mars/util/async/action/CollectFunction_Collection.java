package lab.mars.util.async.action;

import lab.mars.util.async.AsyncStream;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by haixiao on 8/6/2015.
 * Email: wumo@outlook.com
 */
public class CollectFunction_Collection implements WhenAction {
    Collection<AsyncStream> asyncs;

    public CollectFunction_Collection(Collection<AsyncStream> asyncs) {
        this.asyncs = asyncs;
    }

    public void run(AsyncStream callback) {
        AtomicInteger count = new AtomicInteger(0);
        int size = asyncs.size();
        Object[] result = new Object[size];
        int i = 0;
        for (AsyncStream async : asyncs) {
            int _i = i++;
            async.then(e -> {
                result[_i] = e;
                int last = count.incrementAndGet();
                if (last == size) {//the last one to execute
                    callback.onEvent(result);
                }
            }).end();
        }
    }
}
