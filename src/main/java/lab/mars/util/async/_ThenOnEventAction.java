package lab.mars.util.async;

import lab.mars.util.async.action.ThenOnEventAction;

/**
 * Created by wumo on 2016/5/31.
 * email: wumo@outlook.com
 */
public class _ThenOnEventAction extends _OnEventAction {
    private ThenOnEventAction thenOnEventAction = null;

    public _ThenOnEventAction(ThenOnEventAction thenOnEventAction) {
        this.thenOnEventAction = thenOnEventAction;
    }

    @Override protected void onEvent(AsyncStream asyncStream,Object event) {
        thenOnEventAction.onEvent(event);
    }
}
