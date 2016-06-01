package lab.mars.util.async;

import lab.mars.util.async.action.*;
import lab.mars.util.async.internal.SpecialQueue;

import java.util.Collection;

/**
 * <p>
 * Created by haixiao on 2015/3/20.<br>
 * Email: wumo@outlook.com
 * </p>
 * 异步数据流与异步处理流的合体，提供Promise-like的编程方式（但并不是Promise）。<br>
 * <p>整个数据结构如下所示：<br>
 * action_i&lt;-...action_2&lt;-action_1&lt;-O-&gt;event_1-&gt;event_2-&gt;...-&gt;event_i <br>
 * 左端可以动态添加任意类型任意多的action（通过{@link AsyncStream#then}、{@link AsyncStream#when}、{@link AsyncStream#collect}等等方法添加），
 * 而右侧则可以动态添加任意类型任意多的事件对象（通过{@link AsyncStream#onEvent}方法添加）。</p>
 * <p>事件的消费顺序是：中心点O左侧的action依照添加的顺序依次消费右侧的事件（事件也是按照添加的顺序被消费）。
 * 如action_1消费event_1，然后action_2消费event_2等等。</p>
 * <p>每个action的执行需要一定的条件，如{@link AsyncStream#then(ThenOnEventAction)}
 * 需要消费一个事件才可执行，如果没有则等待（异步等待，事件到达时触发执行）；
 * action执行完毕也有条件，如{@link AsyncStream#when}，则是必须等待多个AsyncStream
 * 都结束了才算执行完毕。</p>
 *<p>action由action添加方法（{@link AsyncStream#then}等）或事件添加方法（{@link AsyncStream#onEvent}）
 * 触发执行，目前只会在触发的线程中执行（单线程顺序执行）。</p>
 * <p>应用时需要注意的是{@link AsyncStream#end}方法，此方法是用来结束action链
 * 的定义的，如果不调用{@link AsyncStream#end}方法，则无法判断此AsyncStream是否已经结束。<br>
 *     调用{@link AsyncStream#end}方法后，之后通过{@link AsyncStream#then}等方法添加的action会被直接忽视掉，但{@link AsyncStream#whenEnd}添加的action会起作用</p>
 * <p>{@link AsyncStream#whenEnd}添加的action是用来在AsyncStream结束之后触发执行的，这些action可以要求消费事件，但不会回传事件（可以从{@link AsyncStream#whenEnd}的
 * 函数重载类型中看出，只有两种不会回传事件的Action）。</p>
 * <p>另外关于错误处理，通过{@link AsyncStream#exception(ExceptionHandler)}方法，设置一个统一的错误处理方法，当AsyncStream执行过程中发生错误时，将立即结束流，即{@link AsyncStream#isEnd}将返回true；也可以主动提交错误致使流结束{@link AsyncStream#onException(Throwable)}。</p>
 */
public class AsyncStream extends AsyncStreamAtomicRef {
    /*
     *@fmt:off
     * AsyncStream设计细节。
     * 每个action都有开始执行的条件和结束执行的条件。
     *      o开始执行的条件：由action的参数列表表示，无参数的action无开始执行条件（
     *          即按顺序轮到此action执行时便可立即执行），而有参数的action，则需要获取
     *          与参数数量相等的event才可执行（即此action必须等到event队列中至少有足
     *          够的event时才可执行）；
     *      o结束执行的条件：由action的返回值决定，无返回值的action无结束执行条件
     *          （即action结束后便可立即尝试执行下一个action），而具有返回值
     * @fmt:on
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

    public final AsyncStream await(AsyncStream anotherAsync) {
        if (anotherAsync == null) return this;
        dynamicAddAction(new _AwaitAsyncStream(anotherAsync));
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
        assert action != null;

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
