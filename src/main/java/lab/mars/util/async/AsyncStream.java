package lab.mars.util.async;

import lab.mars.util.async.action.*;
import lab.mars.util.async.internal.SpecialQueue;

import java.util.Collection;

/**
 * Created by haixiao on 2015/3/20.
 * Email: wumo@outlook.com
 * <br>
 * Promise-like asynchronous event handling chain.
 * <br>
 * AsyncStream is designed for async event, but doesn't provide background thread pool for saving async state.
 * <br>
 * <b>Note: This class provides static insertion order for inner async actions. only outermost async can add dynamic actions!! </b>
 */
public class AsyncStream extends AsyncStreamAtomicRef {
    /*
     *AsyncStream的语义：
     * AsyncStream表示异步事件处理流。
     * AsyncStream主要由两个并发队列构成：一个是事件缓存队列；一个是处理器缓存队列。
     *
     */
    private static final _Action END = new _Action() {
        @Override protected void run(AsyncStream asyncStream) {}
    };
    static final Object NULL = new Object();
    private SpecialQueue<Object> events = new SpecialQueue<>();
    private SpecialQueue<_Action> actions = new SpecialQueue<>();
    private SpecialQueue<_Action> whenEndChain = new SpecialQueue<>();

    private ExceptionHandler exceptionHandler = null;


    /**
     * @param isInstant
     *         true if no need to wait for one event to trigger engine processing
     */
    private AsyncStream(boolean isInstant) {
        this.set_status(isInstant ? INSTANT : DEFERRED);
        this.set_tick_mutex(false);
        this.set_chainClosed(false);
    }

    /**
     * provide some events to consume. these events are executed when constructor is called
     */
    private AsyncStream(Object[] events) {
        this(true);//at least one event, trigger engine processing
        if (events == null)
            addLast(NULL);
        else
            for (Object event : events)
                addLast(event);
    }

    //region ...不同的初始化函数

    /**
     * DEFERRED to wait for one async event to happen
     */
    public static AsyncStream deferredAsync() {return new AsyncStream(false);}

    /**
     * no need to wait for one async event to happen, Instant actions will executed instantly
     */
    public static AsyncStream instantAsync(Object... events) {return new AsyncStream(events);}

    public static AsyncStream whenAsync(Collection<AsyncStream> asyncs) {
        return instantAsync().when(asyncs);
    }

    public static AsyncStream whenAsync(AsyncStream... asyncs) {
        return instantAsync().when(asyncs);
    }

    public static AsyncStream collectAsync(Collection<AsyncStream> asyncs) {
        return instantAsync().collect(asyncs);
    }

    public static AsyncStream collectAsync(AsyncStream... asyncs) {
        return instantAsync().collect(asyncs);
    }
    //endregion

    //region ...不同的异步操作

    /**
     * thenAction is executed after async event happened (though itself doesn't consume events)
     */
    public final AsyncStream then(ThenAction thenAction) {
        dynamicAddAction(new _ThenAction(thenAction));
        return this;
    }

    /**
     * thenOnEventAction is executed after async event happened and consume the event
     */
    public final <T> AsyncStream then(ThenOnEventAction<T> thenOnEventAction) {
        dynamicAddAction(new _ThenOnEventAction(thenOnEventAction));
        return this;
    }

    /**
     * thenFunction is just like thenAction and differs only in that thenFunction returns something. if it returns another asyncStream then
     * subsequent
     * action will be triggered by this asyncStream, if return other object,  that object is pushed into events queue.
     */

    public final <R> AsyncStream then(ThenFunction<R> thenFunction) {
        dynamicAddAction(new _ThenFunction(thenFunction));
        return this;
    }

    public final <R, T> AsyncStream then(ThenOnEventFunction<R, T> thenOnEventFunction) {
        dynamicAddAction(new _ThenOnEventFunction(thenOnEventFunction));
        return this;
    }

    public final <T> AsyncStream loop(LoopOnEventAction<T> andLoopAction) {
        dynamicAddAction(new _LoopOnEventAction(andLoopAction));
        return this;
    }

    /**
     * wait until all of the  asyncstreams end.
     */
    public final AsyncStream when(Collection<AsyncStream> asyncs) {
        if (asyncs == null || asyncs.size() == 0) return this;
        dynamicAddAction(new _WhenAction_Collection(asyncs));
        return this;
    }

    /**
     * wait until all of the  asyncstreams end.
     */
    public final AsyncStream when(AsyncStream... asyncs) {
        if (asyncs == null || asyncs.length == 0) return this;
        dynamicAddAction(new _WhenAction_Array(asyncs));
        return this;
    }

    /**
     * wait until all of the  asyncstreams end and return an array Object[] contains all the result from each asyncstream
     */
    public final AsyncStream collect(Collection<AsyncStream> asyncs) {
        if (asyncs == null || asyncs.isEmpty()) return this;
        dynamicAddAction(new _CollectionFunction_Collection(asyncs));
        return this;
    }

    /**
     * wait until all of the  asyncstreams end and return an array Object[] contains all the result from each asyncstream
     */
    public final AsyncStream collect(AsyncStream... asyncs) {
        if (asyncs == null || asyncs.length == 0) return this;
        dynamicAddAction(new _CollectionFunction_Array(asyncs));
        return this;
    }

    /**
     * 当此Async结束时调用此action。与{@link AsyncStream#end(ThenAction)}类似，但不关闭chain(即{@link AsyncStream#chainClosed()}返回false
     */
    public final AsyncStream whenEnd(ThenAction endAction) {
        if (endAction != null) whenEndChain.offer(new _ThenAction(endAction));
        tick();
        return this;
    }

    public final <T> AsyncStream whenEnd(ThenOnEventAction<T> endAction) {
        if (endAction != null) whenEndChain.offer(new _ThenOnEventAction(endAction));
        tick();
        return this;
    }

    /**
     * add one end action to indicate the stream is end
     */
    public final AsyncStream end() {
        dynamicAddAction(END);
        return this;
    }

    /**
     * add one end action to indicate the stream is end。
     */
    public final AsyncStream end(ThenAction endAction) {
        if (endAction != null) whenEndChain.offer(new _ThenAction(endAction));
        end();
        return this;
    }

    /**
     * add one end action to indicate the stream is end。
     */
    public final <T> AsyncStream end(ThenOnEventAction<T> endAction) {
        if (endAction != null) whenEndChain.offer(new _ThenOnEventAction(endAction));
        end();
        return this;
    }

    //endregion

    @CalledByMultiThreads
    private void dynamicAddAction(_Action typedAction) {
        if (typedAction == null) return;
        if (typedAction != END || cas_chainClosed(false, true))
            actions.offer(typedAction);//use END to close chain

        tick();//tick executed here is for INSTANT actions or provided events.
    }

    /**
     * 这里表示在awaitMode==AWAIT期间，不接受新event的缓存（也可以设计成可以缓
     * 存event，但意义也许不明，且实现要麻烦一点），如果不缓存，只要用户可以在调
     * 用CollecFunction之前没有缓存event那么CollectFunction之后的then都是用的最
     * 新的从CollectFunction返回的event。不禁止的话，就无法保证这点。
     */
    /*onEvent方法会在多线程中调用，而tick方法由tick_mutex保护，仅由一个线程执行*/
    @CalledByMultiThreads
    public final void onEvent(Object event) {
        if (isEnd()) return;
        addLast(event);
        //此CAS操作的cost仅在初始情况下发生一次。
        //此处使用CAS更新awaitMode的原因是，如果不是cas，那么设置instant的操作
        // 可以无限阻塞（意思是可以之后随时执行），这会使得WhenAction中设置awaitMode=AWAIT无效化。
        cas_status(DEFERRED, INSTANT);
        tick();
    }

    /**
     * 提交空事件，用来触发instantAction而不用压入额外的事件。
     */
    public final void onEvent() {
        if (isEnd()) return;
        cas_status(DEFERRED, INSTANT);
        tick();
    }

    @SuppressWarnings("unchecked")
    private void tick() {
        /**因为所有情况下只有一个tick_mutex==true，而cas_tick_mutex操作要比get_tick_mutex()
         * 更加费时，所以对于大部分false的情况下，先用get_tick_mutex预先进行条件短路
         * 可以提高效率*/
        if (!cas_tick_mutex(false, true))
            return;

        outer:
        while (true)
            if (isEnd()) {
                while (keep_tick_mutex_if(whenEndChain::notEmpty)
                        && executeAction(whenEndChain)) ;
                break;
            } else {
                //只有没有任何阻碍的INSTANT mode才尝试执行action
                while (keep_tick_mutex_if(() -> get_status() == INSTANT && actions.notEmpty())) {
                    _Action action = actions.peek();
                    if (action == END) {
                        set_status(FINISH);
                        continue outer;
                    } else if (!executeAction(actions))
                        return;
                }
                break;
            }
    }

    private boolean executeAction(SpecialQueue<_Action> queue) {
        _Action action = queue.peek();
        if (!keep_tick_mutex_if(() -> action.precondition(this)))
            return false;
        try {
            action.run(this);
        } catch (Exception e) {
            _exception(e);
        }
        if (action.postcondition(this))
            queue.poll();
        return true;
    }


    public final boolean chainClosed() {
        return get_chainClosed();
    }

    public final boolean isEnd() {
        return get_status() == FINISH;
    }

    /**
     * add exception handler to process exception. this handler could also process other asyncStreams. don't repeat adding exceptionHandler
     */

    public AsyncStream exception(ExceptionHandler exceptionHandler) {
        if (exceptionHandler == null || this.exceptionHandler != null) return this;
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    /**
     * @return if exception has been handled
     */
    public boolean onException(Throwable e) {
        if (exceptionHandler != null) {
            exceptionHandler.handle(e);
            return true;
        }
        return false;
    }

    private void _exception(Throwable e) {
        if (exceptionHandler != null)
            exceptionHandler.handle(e);
        else
            e.printStackTrace();
        set_status(FINISH);//发生错误后，结束执行

    }

    @CalledBySingleThread
    void addFirst(Object event) {
        events.push(event == null ? NULL : event);
    }

    @CalledByMultiThreads
    void addLast(Object event) {
        events.offer(event == null ? NULL : event);
    }

    @CalledBySingleThread
    Object pollEvent() {
        Object event = events.poll();
        return event == NULL ? null : event;
    }

    @CalledBySingleThread
    Object pollRawEvent() {
        return events.poll();
    }

    boolean hasMoreEvents() {
        return !events.isEmpty();
    }

    //awakeMe方法可能在另一线程中调用，但由于WhenAction或awaitAsync的语义使
    // 得不同的设置awaitMode之间存在happen-before关系，所以正确性并无问题
    protected void wakeUp(Object returnFromAwait) {
        if (get_status() != AWAIT) return;//这种情况应该算是Exception
        if (returnFromAwait != null)
            this.addFirst(returnFromAwait);
        this.set_status(INSTANT);
        this.onEvent();
    }
}
