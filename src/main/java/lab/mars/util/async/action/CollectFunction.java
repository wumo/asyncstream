package lab.mars.util.async.action;

import lab.mars.util.async.AsyncStream;
import lab.mars.util.async.InstantAction;

/**
 * Created by haixiao on 8/6/2015.
 * Email: wumo@outlook.com
 */
public interface CollectFunction extends InstantAction {
    void run(AsyncStream callback);
}
