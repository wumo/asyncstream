package lab.mars.util.async.action;

import lab.mars.util.async.Actions;
import lab.mars.util.async.OnEventAction;

import static lab.mars.util.async.Actions.ThenOnEventFunction;

public interface ThenOnEventFunction<R, T> extends OnEventAction {
    R onEvent(T event);

    default Actions type() {
        return ThenOnEventFunction;
    }
}