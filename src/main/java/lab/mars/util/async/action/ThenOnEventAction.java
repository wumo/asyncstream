package lab.mars.util.async.action;

import lab.mars.util.async.Actions;
import lab.mars.util.async.OnEventAction;

import static lab.mars.util.async.Actions.ThenOnEventAction;

public interface ThenOnEventAction<T> extends OnEventAction {
    void onEvent(T event);
    default Actions type() {
        return ThenOnEventAction;
    }
}