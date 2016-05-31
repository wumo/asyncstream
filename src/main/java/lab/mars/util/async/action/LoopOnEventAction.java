package lab.mars.util.async.action;

public interface LoopOnEventAction<T>  {
    /**
     * @return true if continue to loop, otherwise false
     */
    boolean onEvent(T event);

}