package lab.mars.util.async.action;

import lab.mars.util.async.Actions;
import lab.mars.util.async.InstantAction;

import static lab.mars.util.async.Actions.ThenFunction;

public interface ThenFunction<R> extends InstantAction {
    R run();

    default Actions type() {
        return ThenFunction;
    }
}