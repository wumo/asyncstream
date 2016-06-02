package lab.mars.util.async;

import static lab.mars.util.async.AsyncStreamAtomicRef.AWAIT;

/**
 * <p>
 * Created by wumo on 2016/6/1.<br>
 * email: wumo@outlook.com<br>
 * </p>
 */
public class _AwaitAsyncStream extends _Action {
    private AsyncStream anotherAsync = null;

    public _AwaitAsyncStream(AsyncStream anotherAsync) {
        this.anotherAsync = anotherAsync;
    }

    @Override protected void run(AsyncStream asyncStream) throws Exception {
//        asyncStream.set_status(AWAIT);
        asyncStream.lazySet_status(AWAIT);//下面的whenEnd包含volatile write，所以此处可以使用lazySet
        anotherAsync.whenEnd(() -> asyncStream.wakeUp(anotherAsync.pollRawEvent()));
    }
}
