package lab.mars.util.async;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static lab.mars.util.async.AsyncStreamAtomicRef.AWAIT;

/**
 * Created by wumo on 2016/5/31.
 * email: wumo@outlook.com
 */
public class _CollectionFunction_Collection extends _Action {
    private Collection<AsyncStream> asyncs;

    public _CollectionFunction_Collection(Collection<AsyncStream> asyncs) {
        this.asyncs = asyncs;
    }

    @Override protected void run(AsyncStream asyncStream) {
//        asyncStream.set_status(AWAIT);
        asyncStream.lazySet_status(AWAIT);
        AtomicInteger count = new AtomicInteger(0);
        int size = asyncs.size();
        Object[] result = new Object[size];
        int i = 0;
        for (AsyncStream async : asyncs) {
            int _i = i++;
            async.whenEnd(e -> {
                result[_i] = e;
                int last = count.incrementAndGet();
                if (last == size) {//the last one to execute
                    asyncStream.wakeUp(result);
                }
            });
        }
    }
}
