package lab.mars.util.async;

import java.util.concurrent.atomic.AtomicInteger;

import static lab.mars.util.async.AsyncStreamAtomicRef.AWAIT;

/**
 * Created by wumo on 2016/5/31.
 * email: wumo@outlook.com
 */
public class _WhenAction_Array extends _Action {
    private AsyncStream[] asyncs;

    public _WhenAction_Array(AsyncStream[] asyncs) {
        this.asyncs = asyncs;
    }

    @Override protected void run(AsyncStream asyncStream) {
//        asyncStream.set_status(AWAIT);
        asyncStream.lazySet_status(AWAIT);
        AtomicInteger count = new AtomicInteger(0);
        int size = asyncs.length;
        for (AsyncStream async : asyncs)
            async.whenEnd(() -> {
                int last = count.incrementAndGet();
                if (last == size) {//the last one to execute
                    asyncStream.wakeUp(null);
                }
            });
    }
}
