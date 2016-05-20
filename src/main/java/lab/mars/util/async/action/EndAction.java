package lab.mars.util.async.action;

import lab.mars.util.async.Actions;

import static lab.mars.util.async.Actions.END_ACTION;

/**
 * Created by haixiao on 8/10/2015.
 * Email: wumo@outlook.com
 */
public interface EndAction extends ThenAction {
    default Actions type() {
        return END_ACTION;
    }
}
