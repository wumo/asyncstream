package lab.mars.util.async.action;

import lab.mars.util.async.Actions;
import lab.mars.util.async.InstantAction;

import static lab.mars.util.async.Actions.ThenAction;

public interface ThenAction extends InstantAction {
    void run();

    default Actions type() {
        return ThenAction;
    }
}