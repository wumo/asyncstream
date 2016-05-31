package lab.mars.util.async;

import lab.mars.util.async.action.ThenAction;

/**
 * Created by wumo on 2016/5/31.
 * email: wumo@outlook.com
 */
public class _ThenAction extends _Action {
    ThenAction thenAction = null;

    public _ThenAction(ThenAction thenAction) {
        this.thenAction = thenAction;
    }

    @Override protected void run(AsyncStream asyncStream) {
        thenAction.run();
    }
}
