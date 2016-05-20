package lab.mars.util.async;

import static lab.mars.util.async.Actions.Undefined;

public interface Action {
    default Actions type() {
        return Undefined;
    }
}