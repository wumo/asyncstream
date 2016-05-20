package lab.mars.util.async;

import lab.mars.util.async.action.*;
import org.jctools.queues.MpscLinkedQueue7;
import org.jctools.queues.MpscLinkedQueue8;
import org.jctools.util.UnsafeAccess;

import java.util.Collection;
import java.util.Queue;
import java.util.function.Supplier;

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
    private static final Supplier<Queue> newQueue = UnsafeAccess.SUPPORTS_GET_AND_SET ? MpscLinkedQueue8::new : MpscLinkedQueue7::new;
    /*
     *实现的具体细节：
     * 主要的数据结构是2个无锁MPSC队列，一个存储接收到的event，一个存储注册的handler；
     * 主要的操作是“atomic process events”和“atomic transfer handlers”。
     *
     */
    private static final EndAction END = () -> {};
    private static final Object NULL = new Object();
    private Queue<Object> events = newQueue.get();
    private Queue<Action> chain =newQueue.get();
    private Queue<EndAction> whenEndChain = newQueue.get();

    /** 表示是否需要一个异步事件来触发后续handler的执行 */
    private volatile boolean canInstantActionExecute;
    private ExceptionHandler exceptionHandler = null;

    /**
     * @param isInstant
     *         true if no need to wait for one event to trigger engine processing
     */
    private AsyncStream(boolean isInstant) {
        this.canInstantActionExecute = isInstant;
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
     * deferred to wait for one async event to happen
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

        this.tick();//tick executed here is for instant actions or provided events.
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

	/*
     *有两个时机可以推动处理进程：
	 * 添加action，
	 * OnEvent
	 * 而这两个可能处在不同的线程中，他们修改的是相同的events队列和chain队列，所以需要处理好同时修改的问题。
	 * handleEngine，由此引擎不断处理events中的事件，此引擎可能“走走停停”，问题的关键是，能不能保证停了能被唤醒，或者停的是时候吗？
	 * 唤醒引擎只有两个时机：
	 * 1.添加action时：
	 * 2.onEvent时：
	 *
	 * 保证events队列和chain队列的逻辑正确性：
	 * 1.events中移除的事件必须要有action处理，否则不能移除（即不允许先移除，发现没action处理，再重新添加回头部的情况），所有新的事件的添加都是添加到尾部
	 * 2.chain中移除action也同样是确定此action不会呆在chain中了才移除（即不允许先移除，发现还要呆在chain中，再重新添加回头部的情况，特指loop）
	 * 3.action前面有action时，它是不会处理events的，即使events不为空（当然这是不可能的，只是指出以前的判断方式有点不妥）
	 * 4.chain中存在action的只可能是events为空的情况下，否则只要events中存在event，那么如果chain中还有action，就一定会处理的，直至chain中为空（这时events中可能不为空）或者events中为空
	 *
	 * 可能onEvent和添加action同时推动处理进程，由于是无锁的，这时需要处理好同时处理的逻辑，即允许“处理到一半的状态的一致性修复”
	 */

    public final void onEvent(Object event) {
        if (isEnd()) return;
        this.canInstantActionExecute = true;//必须在event添加至事件队列前设置（原因是:events队列不为空就意味着canInstantActionExecute = true，为了保证这点就必须保证此语句的先执行）
        this.events.add(event == null ? NULL : event);//cache events if not end
        this.tick();
    }

    /**
     * 提交空事件，用来触发instantAction而不用压入额外的事件。
     */
    public final void onEvent() {
        if (isEnd()) return;
        this.canInstantActionExecute = true;
        this.tick();
    }

    /**
     * 当条件满足时，当前线程让出tick_mutex
     *
     * @param condition
     *         让出tick_mutex的条件
     * @return true则让出tick函数的执行权;false则不让出。
     */
    private boolean tick_mutex_release_condition_satisfied(Supplier<Boolean> condition) {
        while (true) {
            if (condition.get()) {
                set_tick_mutex(false);
                if (condition.get())
                    return true;
                else if (!cas_tick_mutex(false, true))
                    return true;
                else
                    continue;
            }
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void tick() {
        //mutex isOn is to guarantee only one thread will enter the tick function at the same time
        //the process only need one thread.
        if (!cas_tick_mutex(false, true))
            return;

        while (true) {
            if (tick_mutex_release_condition_satisfied(this.chain::isEmpty))
                return;

            Action action = this.chain.peek();
            assert action != null;
            //InstantAction doesn't need to wait for events
            if (action instanceof InstantAction) {
                if (tick_mutex_release_condition_satisfied(() -> !canInstantActionExecute))
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
                    this.canInstantActionExecute = false;//此语句先执行的原因是：下面的WhenAction可能立即返回，这时是可以继续继续执行的
                    ((WhenAction) action).run(this);//就用当前Async不会有问题，因为WhenAction回调前，此Async不会变更的，但是需要防止后续InstantAction执行。
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

    private void awaitAsync(AsyncStream anotherAsync) {
        this.canInstantActionExecute = false;//需要等待内部async的事件到来才可执行InstantAction
        anotherAsync.then(() -> {
            Object returnedEvent = anotherAsync.events.poll();
            if (returnedEvent == null) this.onEvent();
            else this.onEvent(returnedEvent);
        }).end();//内部Async不再添加新的handler
    }
}
