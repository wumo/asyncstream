package lab.mars.util.async;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Supplier;

import static lab.mars.util.async.internal.UnsafeAccess.UNSAFE;

/**
 * Created by haixiao on 2015-08-13.
 * Email: wumo@outlook.com
 * <p/>
 * 本类的效果就是<code>AtomicBoolean chainClosed,tick_mutex;</code>
 * 只不过为了节省内存空间，使用静态的{@link AtomicIntegerFieldUpdater}统一更新volatile变量，
 * 从而节省2个对象的空间大约32 bytes（内存计算参见http://www.javamex.com/tutorials/memory/object_memory_usage.shtml）。。
 */
@sun.misc.Contended//applicable in Java 8 to avoid False Sharing.经测试，好像没什么效果。。。可能因为另两个变量并不会在多线程中多次竞争
public class AsyncStreamAtomicRef {
    protected final static long chainClosed_OFFSET, tick_mutex_OFFSET, status_OFFSET;

    static {
        try {
            chainClosed_OFFSET = UNSAFE.objectFieldOffset(AsyncStreamAtomicRef.class.getDeclaredField("chainClosed"));
            tick_mutex_OFFSET = UNSAFE.objectFieldOffset(AsyncStreamAtomicRef.class.getDeclaredField("tick_mutex"));
            status_OFFSET = UNSAFE.objectFieldOffset(AsyncStreamAtomicRef.class.getDeclaredField("status"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    protected static final int INSTANT = 0;
    protected static final int DEFERRED = 1;
    protected static final int AWAIT = 2;
    protected static final int FINISH = 3;

    private volatile int status;
    private volatile int chainClosed;
    private volatile int tick_mutex;

    protected final int get_status() {
        return status;
    }

    protected final void set_status(int value) {
        status = value;
    }

    protected final boolean cas_status(int expect, int update) {
        if (get_status() != expect) return false;
        return UNSAFE.compareAndSwapInt(this, status_OFFSET, expect, update);
    }

    protected final boolean get_chainClosed() {
        return chainClosed != 0;
    }

    protected final void set_chainClosed(boolean newValue) {
        chainClosed = newValue ? 1 : 0;
    }

    public final boolean cas_chainClosed(boolean expect, boolean update) {
        if (get_chainClosed() != expect) return false;
        int e = expect ? 1 : 0;
        int u = update ? 1 : 0;
        return UNSAFE.compareAndSwapInt(this, chainClosed_OFFSET, e, u);
    }

    protected final boolean get_tick_mutex() {
        return tick_mutex != 0;
    }

    protected final void set_tick_mutex(boolean newValue) {
        tick_mutex = newValue ? 1 : 0;
    }

    protected final boolean cas_tick_mutex(boolean expect, boolean update) {
        if (get_tick_mutex() != expect) return false;
        int e = expect ? 1 : 0;
        int u = update ? 1 : 0;
        return UNSAFE.compareAndSwapInt(this, tick_mutex_OFFSET, e, u);
    }

    /**
     * 当条件不满足时，当前线程让出tick_mutex<p>
     * Double Checking
     *
     * @param condition
     *         保持tick_mutex的条件
     * @return false则让出tick函数的执行权;true则不让出。
     */
    protected boolean keep_tick_mutex_if(Supplier<Boolean> condition) {
        while (true) {
            if (!condition.get()) {
                set_tick_mutex(false);
                if (!condition.get())
                    return false;
                else if (!cas_tick_mutex(false, true))
                    return false;
                else
                    continue;
            }
            return true;
        }
    }
}
