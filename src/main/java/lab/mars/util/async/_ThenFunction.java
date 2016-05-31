package lab.mars.util.async;

import lab.mars.util.async.action.ThenFunction;

import static lab.mars.util.async.AsyncStreamAtomicRef.AWAIT;

/**
 * Created by wumo on 2016/5/31.
 * email: wumo@outlook.com
 */
public class _ThenFunction extends _Action {
    private ThenFunction thenFunction = null;

    public _ThenFunction(ThenFunction thenFunction) {
        this.thenFunction = thenFunction;
    }

    @Override protected void run(AsyncStream asyncStream) {
        Object result = thenFunction.run();
        if (result instanceof AsyncStream) {
            asyncStream.set_status(AWAIT);
            AsyncStream anotherAsync = (AsyncStream) result;
            anotherAsync.whenEnd(() -> asyncStream.wakeUp(anotherAsync.pollEvent()));
        } else
            asyncStream.addFirst(result);
    }
}
