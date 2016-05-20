package lab.mars.util.async.action;

import lab.mars.util.async.Actions;
import lab.mars.util.async.AsyncStream;
import lab.mars.util.async.InstantAction;

import static lab.mars.util.async.Actions.WhenAction;

/**
 * Created by haixiao on 8/6/2015.
 * Email: wumo@outlook.com
 */
public interface WhenAction extends InstantAction {
    void run(AsyncStream callback);

    default Actions type() {
        return WhenAction;
    }
}
