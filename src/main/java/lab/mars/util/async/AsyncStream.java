package lab.mars.util.async;

import lab.mars.util.async.action.*;
import org.jctools.queues.QueueFactory;

import java.util.Collection;
import java.util.Queue;

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
     *实现的具体细节：
     * 主要的数据结构是2个无锁MPSC队列，一个存储接收到的event，一个存储注册的handler；
     * 主要的操作是“atomic process events”和“atomic transfer handlers”。
     *
     */
    private static final EndAction END = () -> {};
    private static final Object NULL = new Object();
    private Queue<Object> events = QueueFactory.newUnboundedMpsc();
    private Queue<Action> chain = QueueFactory.newUnboundedMpsc();
    private Queue<EndAction> whenEndChain = QueueFactory.newUnboundedMpsc();

    private ExceptionHandler exceptionHandler = null;


    /**
     * @param isInstant
     *         true if no need to wait for one event to trigger engine processing
     */
    private AsyncStream(boolean isInstant) {
        this.set_awaitMode(isInstant ? INSTANT : DEFERRED);
        this.set_tick_mutex(false);
        this.set_chainClosed(false);
    }

    /**
     * provide some events to consume. these events are executed when constructor is called
     */
    private AsyncStream(Object[] events) {
        this(true);//at least one event, trigger engine processing
        if (events == null)
            this.events.add(NULL);
        else
            for (Object event : events)
                this.events.add(event == null ? NULL : event);
    }

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

    /**
     * thenAction is executed after async event happened (though itself doesn't consume events)
     */
    public final AsyncStream then(ThenAction thenAction) {
        dynamicAddAction(thenAction);
        return this;
    }

    /**
     * thenOnEventAction is executed after async event happened and consume the event
     */
    public final <T> AsyncStream then(ThenOnEventAction<T> thenOnEventAction) {
        dynamicAddAction(thenOnEventAction);
        return this;
    }

    /**
     * thenFunction is just like thenAction and differs only in that thenFunction returns something. if it returns another asyncStream then
     * subsequent
     * action will be triggered by this asyncStream, if return other object,  that object is pushed into events queue.
     */

    public final <R> AsyncStream then(ThenFunction<R> thenFunction) {
        dynamicAddAction(thenFunction);
        return this;
    }

    public final <R, T> AsyncStream then(ThenOnEventFunction<R, T> thenOnEventFunction) {
        dynamicAddAction(thenOnEventFunction);
        return this;
    }

    public final <T> AsyncStream loop(LoopOnEventAction<T> andLoopAction) {
        dynamicAddAction(andLoopAction);
        return this;
    }

    /**
     * wait until all of the  asyncstreams end.
     */
    public final AsyncStream when(Collection<AsyncStream> asyncs) {
        if (asyncs == null || asyncs.size() == 0) return this;
        dynamicAddAction(new WhenAction_Collection(asyncs));
        return this;
    }

    /**
     * wait until all of the  asyncstreams end.
     */
    public final AsyncStream when(AsyncStream... asyncs) {
        if (asyncs == null || asyncs.length == 0) return this;
        dynamicAddAction(new WhenAction_Array(asyncs));
        return this;
    }

    /**
     * wait until all of the  asyncstreams end and return an array Object[] contains all the result from each asyncstream
     */
    public final AsyncStream collect(Collection<AsyncStream> asyncs) {
        if (asyncs == null || asyncs.isEmpty()) return this;
        dynamicAddAction(new CollectFunction_Collection(asyncs));
        return this;
    }

    /**
     * wait until all of the  asyncstreams end and return an array Object[] contains all the result from each asyncstream
     */
    public final AsyncStream collect(AsyncStream... asyncs) {
        if (asyncs == null || asyncs.length == 0) return this;
        dynamicAddAction(new CollectFunction_Array(asyncs));
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
    public final AsyncStream end(EndAction endAction) {
        dynamicAddAction(endAction == null ? END : endAction);
        return this;
    }

    /**
     * 当此Async结束时调用此action。与{@link AsyncStream#end(EndAction)}类似，但不关闭chain(即{@link AsyncStream#chainClosed()}返回false
     */
    public final AsyncStream whenEnd(EndAction endAction) {
        if (endAction != null) whenEndChain.add(endAction);
        this.tick();
        return this;
    }

    public final boolean chainClosed() {
        return get_chainClosed();
    }

    public final boolean isEnd() {
        Action action = chain.peek();
        return action == END;
    }

    private void dynamicAddAction(Action typedAction) {
        if (typedAction == null) return;
        if (typedAction instanceof EndAction) {
            if (cas_chainClosed(false, true)) //first one that ends this chain
                chain.add(END);//use END to close chain
            if (typedAction != END)
                whenEndChain.add((EndAction) typedAction);
        } else
            chain.add(typedAction);

        this.tick();//tick executed here is for INSTANT actions or provided events.
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
    }

    /**
     * 这里表示在awaitMode==AWAIT期间，不接受新event的缓存（也可以设计成可以缓
     * 存event，但意义也许不明，且实现要麻烦一点），如果不缓存，只要用户可以在调
     * 用CollecFunction之前没有缓存event那么CollectFunction之后的then都是用的最
     * 新的从CollectFunction返回的event。不禁止的话，就无法保证这点。
     */
    /*onEvent方法会在多线程中调用，而tick方法由tick_mutex保护，仅由一个线程执行*/
    public final void onEvent(Object event) {
        if (isEnd() || get_awaitMode() == AWAIT) return;
        this.events.add(event == null ? NULL : event);
        if (this.get_awaitMode() == DEFERRED)
            //此CAS操作的cost仅在初始情况下发生一次。
            //此处使用CAS更新awaitMode的原因是，如果不是cas，那么设置instant的操作
            // 可以无限阻塞（意思是可以之后随时执行），这会使得WhenAction中设置awaitMode=AWAIT无效化。
            this.cas_awaitMode(DEFERRED, INSTANT);
        this.tick();
    }

    /**
     * 提交空事件，用来触发instantAction而不用压入额外的事件。
     */
    public final void onEvent() {
        if (isEnd() || get_awaitMode() == AWAIT) return;
        if (this.get_awaitMode() == DEFERRED)
            this.cas_awaitMode(DEFERRED, INSTANT);
        this.tick();
    }

    @SuppressWarnings("unchecked")
    private void tick() {
        /**因为所有情况下只有一个tick_mutex==true，而cas_tick_mutex操作要比get_tick_mutex()
         * 更加费时，所以对于大部分false的情况下，先用get_tick_mutex预先进行条件短路
         * 可以提高效率*/
        if (get_tick_mutex() || !cas_tick_mutex(false, true))
            return;

        while (true) {
            if (tick_mutex_release_condition_satisfied(this.chain::isEmpty))
                return;

            Action action = this.chain.peek();
            assert action != null;
            //InstantAction doesn't need to wait for events
            if (action instanceof InstantAction) {
                if (tick_mutex_release_condition_satisfied(() -> get_awaitMode() != INSTANT))
                    return;
                if (action == END) {//proceed to end action, gonna finish
                    while (!tick_mutex_release_condition_satisfied(whenEndChain::isEmpty)) {
                        EndAction endAction = this.whenEndChain.poll();
                        try {
                            endAction.run();
                        } catch (Throwable e) {
                            _exception(e);
                        }
                    }
                    return;
                } else
                    executeInstantAction(action);
            } else {
                if (tick_mutex_release_condition_satisfied(this.events::isEmpty))
                    return;

                Object event = this.events.poll();//consume event
                if (event == NULL) event = null;
                executeOnEventAction(action, event);
            }
        }
    }

    private void executeInstantAction(Action action) {
        try {
            switch (action.type()) {
                case ThenAction:
                    ((ThenAction) action).run();
                    break;
                case ThenFunction:
                    Object result = ((ThenFunction) action).run();
                    if (result instanceof AsyncStream)
                        awaitAsync((AsyncStream) result);
                    else
                        this.events.add(result == null ? NULL : result);//将返回的结果加入到事件队列的尾端
                    break;
                case WhenAction:
                    this.set_awaitMode(AWAIT);
                    ((WhenAction) action).run(this::awakeMe);
                    break;
                default:
                    break;
            }
            this.chain.poll();
        } catch (Throwable e) {
            _exception(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void executeOnEventAction(Action action, Object event) {
        try {
            boolean removeAction = true;
            //处理事件
            switch (action.type()) {
                case ThenOnEventAction:
                    ((ThenOnEventAction) action).onEvent(event);
                    break;
                case ThenOnEventFunction:
                    Object result = ((ThenOnEventFunction) action).onEvent(event);
                    if (result instanceof AsyncStream)
                        awaitAsync((AsyncStream) result);
                    else
                        this.events.add(result == null ? NULL : result);//将返回的结果加入到事件队列的尾端
                    break;
                case LoopOnEventAction:
                    if (((LoopOnEventAction) action).onEvent(event))
                        removeAction = false;
                    break;
                default:
                    break;
            }
            if (removeAction) this.chain.poll();
        } catch (Throwable e) {
            _exception(e);
        } finally {
            if (event instanceof Cleanable)
                ((Cleanable) event).clean();
        }
    }

    //awakeMe方法可能在另一线程中调用，但由于WhenAction或awaitAsync的语义使
    // 得不同的设置awaitMode之间存在happen-before关系，所以正确性并无问题
    private void awakeMe(Object returnFromAnotherAsync) {
        if (returnFromAnotherAsync != null)
            this.events.add(returnFromAnotherAsync);//这里不判断NULL对象的使用是因为，如果是从awaitAsync返回，则NULL已经处理，另一种会返回的只有CollectFunction，它只返回数组，所以也不用判断
        this.set_awaitMode(INSTANT);//必须在event添加之后设置，否则中间过程可能夹杂其他事件，不能保证最新
        this.onEvent();
    }

    private void awaitAsync(AsyncStream anotherAsync) {
        this.set_awaitMode(AWAIT);
        anotherAsync.then(() -> this.awakeMe(anotherAsync.events.poll())).end();//内部Async不再添加新的handler
    }
}
