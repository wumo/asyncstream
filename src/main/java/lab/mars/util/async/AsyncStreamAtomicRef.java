package lab.mars.util.async;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Supplier;

import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * Created by haixiao on 2015-08-13.
 * Email: wumo@outlook.com
 * <p/>
 * 本类的效果就是<code>AtomicBoolean chainClosed,tick_mutex;</code>
 * 只不过为了节省内存空间，使用静态的{@link AtomicIntegerFieldUpdater}统一更新volatile变量，
 * 从而节省2个对象的空间大约32 bytes（内存计算参见http://www.javamex.com/tutorials/memory/object_memory_usage.shtml）。。
 * （增益也许并不大，主要是为了学习AtomicIntegerFieldUpdater的使用）
 */
@sun.misc.Contended//applicable in Java 8 to avoid False Sharing.
public class AsyncStreamAtomicRef {
    protected final static long chainClosed_OFFSET, tick_mutex_OFFSET, awaitMode_OFFSET;

    static {
        try {
            chainClosed_OFFSET = UNSAFE.objectFieldOffset(AsyncStreamAtomicRef.class.getDeclaredField("chainClosed"));
            tick_mutex_OFFSET = UNSAFE.objectFieldOffset(AsyncStreamAtomicRef.class.getDeclaredField("tick_mutex"));
            awaitMode_OFFSET = UNSAFE.objectFieldOffset(AsyncStreamAtomicRef.class.getDeclaredField("awaitMode"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    static final int DEFERRED = 0;
    static final int INSTANT = 1;
    static final int AWAIT = 2;

    private volatile int awaitMode;
    private volatile int chainClosed;
    private volatile int tick_mutex;

    protected final int get_awaitMode() {
        return awaitMode;
    }

    protected final void set_awaitMode(int value) {
        awaitMode = value;
    }

    protected final boolean cas_awaitMode(int expect, int update) {
        return UNSAFE.compareAndSwapInt(this, awaitMode_OFFSET, expect, update);
    }

    protected final boolean get_chainClosed() {
        return chainClosed != 0;
    }

    protected final void set_chainClosed(boolean newValue) {
        chainClosed = newValue ? 1 : 0;
    }

    public final boolean cas_chainClosed(boolean expect, boolean update) {
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
        int e = expect ? 1 : 0;
        int u = update ? 1 : 0;
        return UNSAFE.compareAndSwapInt(this, tick_mutex_OFFSET, e, u);
    }

    /**
     * 当条件满足时，当前线程让出tick_mutex<p>
     * Double Checking
     *
     * @param condition
     *         让出tick_mutex的条件
     * @return true则让出tick函数的执行权;false则不让出。
     */
    protected boolean tick_mutex_release_condition_satisfied(Supplier<Boolean> condition) {
        while (true) {
            if (condition.get()) {
                set_tick_mutex(false);
                if (condition.get())
                    return true;
                else if (get_tick_mutex() || !cas_tick_mutex(false, true))
                    return true;
                else
                    continue;
            }
            return false;
        }
    }
}
