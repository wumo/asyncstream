package lab.mars.util.async.action;

public interface ThenOnEventFunction<R, T> {
    R onEvent(T event);
}