package lab.mars.util.async;

import lab.mars.util.async.action.ThenOnEventFunction;

import static lab.mars.util.async.AsyncStreamAtomicRef.AWAIT;

/**
 * Created by wumo on 2016/5/31.
 * email: wumo@outlook.com
 */
public class _ThenOnEventFunction extends _OnEventAction {
    private ThenOnEventFunction thenOnEventFunction = null;

    public _ThenOnEventFunction(ThenOnEventFunction thenOnEventFunction) {
        this.thenOnEventFunction = thenOnEventFunction;
    }

    @Override protected void onEvent(AsyncStream asyncStream, Object event) {
        Object result = thenOnEventFunction.onEvent(event);
        if (result instanceof AsyncStream) {
            asyncStream.set_status(AWAIT);
            AsyncStream anotherAsync = (AsyncStream) result;
            anotherAsync.whenEnd(() -> asyncStream.wakeUp(anotherAsync.pollRawEvent()));
        } else
            asyncStream.addFirst(result);
    }
}