package lab.mars.util.async;

import lab.mars.util.async.action.ThenAction;
import lab.mars.util.async.action.ThenOnEventAction;

/**
 * Created by wumo on 2016/5/26.
 * email: wumo@outlook.com
 */
public interface AbstractAsyncStream {

    AbstractAsyncStream then(ThenAction thenAction);
    <EventType>AbstractAsyncStream then(ThenOnEventAction<EventType> thenOnEventAction);
}
