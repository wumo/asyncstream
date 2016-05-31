package lab.mars.util.async;

import lab.mars.util.async.action.LoopOnEventAction;

/**
 * Created by wumo on 2016/5/31.
 * email: wumo@outlook.com
 */
public class _LoopOnEventAction extends _OnEventAction {
    private LoopOnEventAction loopOnEventAction = null;
    private boolean terminate = true;

    public _LoopOnEventAction(LoopOnEventAction loopOnEventAction) {
        this.loopOnEventAction = loopOnEventAction;
    }

    @Override protected void onEvent(AsyncStream asyncStream,Object event) {
        terminate = !loopOnEventAction.onEvent(event);
    }

    @Override protected boolean postcondition(AsyncStream asyncStream) {
        return terminate;
    }
}
