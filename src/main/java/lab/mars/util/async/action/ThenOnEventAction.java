package lab.mars.util.async.action;

public interface ThenOnEventAction<T> {
    void onEvent(T event);
}