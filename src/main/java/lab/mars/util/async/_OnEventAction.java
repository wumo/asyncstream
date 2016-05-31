package lab.mars.util.async;

/**
 * Created by wumo on 2016/5/31.
 * email: wumo@outlook.com
 */
public abstract class _OnEventAction extends _Action {
    @Override
    protected boolean precondition(AsyncStream asyncStream) {
        return asyncStream.hasMoreEvents();
    }

    protected abstract void onEvent(AsyncStream asyncStream, Object event);

    @Override protected void run(AsyncStream asyncStream) {
        Object event = asyncStream.pollEvent();
        onEvent(asyncStream, event);
        //有些event可能需要clean
        if (event instanceof Cleanable)
            ((Cleanable) event).clean();
    }
}
