package lab.mars.util.async;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static lab.mars.util.async.AsyncStreamAtomicRef.AWAIT;

/**
 * Created by wumo on 2016/5/31.
 * email: wumo@outlook.com
 */
public class _WhenAction_Collection extends _Action {
    private Collection<AsyncStream> asyncs;

    public _WhenAction_Collection(Collection<AsyncStream> asyncs) {
        this.asyncs = asyncs;
    }

    @Override protected void run(AsyncStream asyncStream) {
//        asyncStream.set_status(AWAIT);
        asyncStream.lazySet_status(AWAIT);//下面的whenEnd包含volatile write，所以此处可以使用lazySet
        AtomicInteger count = new AtomicInteger(0);
        int size = asyncs.size();
        for (AsyncStream async : asyncs) {
            async.whenEnd(() -> {
                int last = count.incrementAndGet();
                if (last == size) {//the last one to execute
                    asyncStream.wakeUp(null);
                }
            });
        }
    }
}
