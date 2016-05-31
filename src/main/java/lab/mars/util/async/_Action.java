package lab.mars.util.async;

/**
 * Created by wumo on 2016/5/31.
 * email: wumo@outlook.com
 */
public abstract class _Action {
    protected boolean precondition(AsyncStream asyncStream) {
        return true;
    }

    protected abstract void run(AsyncStream asyncStream) throws Exception;

    protected boolean postcondition(AsyncStream asyncStream) {
        return true;
    }

}
