package test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadLocalRandom;

import static lab.mars.special_queue.UnsafeAccess.UNSAFE;

/**
 * <p>
 * Created by wumo on 2016/6/3.<br>
 * email: wumo@outlook.com<br>
 * </p>
 */
public class TestMemoryReordering {
    public static final boolean hwfence = false;
    int X, Y;
    int r1, r2;
    int detected = 0;
    int iteration = 0;
    private static final long X_offset, Y_offset, r1_offset, r2_offset;

    static {
        try {
            X_offset = UNSAFE.objectFieldOffset(TestMemoryReordering.class.getDeclaredField("X"));
            Y_offset = UNSAFE.objectFieldOffset(TestMemoryReordering.class.getDeclaredField("Y"));
            r1_offset = UNSAFE.objectFieldOffset(TestMemoryReordering.class.getDeclaredField("r1"));
            r2_offset = UNSAFE.objectFieldOffset(TestMemoryReordering.class.getDeclaredField("r2"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    CyclicBarrier startBarrier = new CyclicBarrier(2, () -> {
        iteration++;
        X = 0;
        Y = 0;
    });
    CyclicBarrier endBarrier = new CyclicBarrier(2, () -> {
        if (r1 == 0 && r2 == 0) {
            detected++;
            System.out.printf("%d reorders detected after %d iterations\n", detected, iteration);
        }
    });

    public void ThreadFunc1() {
        //random wait
        while (true) {
            try {
                startBarrier.await();
                while (ThreadLocalRandom.current().nextInt() % 8 != 0) ;
                UNSAFE.putIntVolatile(this, X_offset, 1);
//                X = 1;
                if (hwfence) UNSAFE.fullFence();
                else {UNSAFE.storeFence(); }
                UNSAFE.putInt(this, r1_offset, UNSAFE.getInt(this, Y_offset));
//                r1 = Y;
                endBarrier.await();
            } catch (BrokenBarrierException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void ThreadFunc2() {
        //random wait
        while (true) {
            try {
                startBarrier.await();
                while (ThreadLocalRandom.current().nextInt() % 8 != 0) ;
                UNSAFE.putIntVolatile(this, Y_offset, 1);
//                Y = 1;
                if (hwfence) UNSAFE.fullFence();
                else {UNSAFE.storeFence();}
                UNSAFE.putInt(this, r2_offset, UNSAFE.getInt(this, X_offset));
//                r2 = X;
                endBarrier.await();
            } catch (BrokenBarrierException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) {
        TestMemoryReordering test = new TestMemoryReordering();
        Thread t1 = new Thread(test::ThreadFunc1);
        Thread t2 = new Thread(test::ThreadFunc2);
        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
