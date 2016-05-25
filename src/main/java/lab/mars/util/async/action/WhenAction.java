package lab.mars.util.async.action;

import lab.mars.util.async.Actions;
import lab.mars.util.async.InstantAction;

import java.util.function.Consumer;

import static lab.mars.util.async.Actions.WhenAction;

/**
 * Created by haixiao on 8/6/2015.
 * Email: wumo@outlook.com
 */
public interface WhenAction extends InstantAction {
    void run(Consumer awakeFuncWithReturn);

    default Actions type() {
        return WhenAction;
    }
}
