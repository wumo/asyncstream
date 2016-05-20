package lab.mars.util.async.action;

import lab.mars.util.async.Actions;
import lab.mars.util.async.OnEventAction;

import static lab.mars.util.async.Actions.LoopOnEventAction;

public interface LoopOnEventAction<T> extends OnEventAction {
    /**
     * @return true if continue to loop, otherwise false
     */
    boolean onEvent(T event);

    default Actions type() {
        return LoopOnEventAction;
    }
}