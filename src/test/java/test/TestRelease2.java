package test;

import static lab.mars.util.async.internal.UnsafeAccess.UNSAFE;

/**
 * <p>
 * Created by wumo on 2016/6/1.<br>
 * email: wumo@outlook.com<br>
 * </p>
 */
public class TestRelease2 {

    @sun.misc.Contended
    static class ShareVariable {
        private static final long x_offset, y_offset;

        static {
            try {
                x_offset = UNSAFE.objectFieldOffset(ShareVariable.class.getDeclaredField("x"));
                y_offset = UNSAFE.objectFieldOffset(ShareVariable.class.getDeclaredField("y"));
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        public int x, y;
        public volatile int r1, r2;

        public void putRelease_x(int v) {
            UNSAFE.putOrderedInt(this, x_offset, v);
        }

        public void putRelease_y(int v) {
            UNSAFE.putOrderedInt(this, y_offset, v);
        }

        public int getAcquire_x() {
            return UNSAFE.getIntVolatile(this, x_offset);
        }

        public int getAcquire_y() {
            return UNSAFE.getIntVolatile(this, y_offset);
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }

    volatile int signal = 0;

    public boolean test() {
        ShareVariable shared = new ShareVariable();
//        int[] t1 = new int[1], t2 = new int[1];
//        boolean[] t1_start = new boolean[]{false}, t2_start = new boolean[]{false};
//        boolean[] t1_end = new boolean[]{false}, t2_end = new boolean[]{false};
        Thread thread1 = new Thread(() -> {
            while (signal == 0) ;
            UNSAFE.fullFence();
//            System.out.println("recv signal");
            shared.x = 1;
            UNSAFE.fullFence();
//            UNSAFE.storeFence();
//            shared.putRelease_x(1);
//            UNSAFE.loadFence();
            int r1 = shared.getY();
            shared.r1 = r1;
            UNSAFE.fullFence();
//            UNSAFE.storeFence();
//            UNSAFE.loadFence();
        });
        Thread thread2 = new Thread(() -> {
            while (signal == 0) ;
//            System.out.println("recv signal");
            UNSAFE.fullFence();
            shared.y = 1;
            UNSAFE.fullFence();
//            UNSAFE.storeFence();
//            UNSAFE.loadFence();
//            shared.putRelease_y(1);
            int r2 = shared.getX();
            shared.r2 = r2;
            UNSAFE.fullFence();
//            UNSAFE.storeFence();
//            UNSAFE.loadFence();
        });
        thread1.start(); thread2.start();
//        while (!t1_start[0] || !t2_start[0]) ;
        try {
            Thread.sleep(10);
//        System.out.println("both start");
            signal = 1;
//        Thread.sleep(100);
//        while (!t1_end[0] || !t2_end[0]) ;
            thread1.join();
            thread2.join();
            if (thread1.isAlive() || thread2.isAlive())
                System.out.println("wrong!");
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
//        System.out.println("both end!");
//        System.out.println("out result");
//        UNSAFE.loadFence();
        UNSAFE.fullFence();
        if (shared.r1 == 0 && shared.r2 == 0) {
            System.out.println("found!");
            return true;
        }
        return false;
    }

    public static void main(String[] args) {
        int count = 10000;
        boolean first = true;
        for (int i = 0; i < count; i++) {
            if (i % 80 == 0)
                System.out.println();
            System.out.println(i);
            TestRelease2 testRelease = new TestRelease2();
            if (testRelease.test()) {
                try {
                    System.gc();
                    Thread.sleep(10000);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (first) {
                    first = false;
                    System.out.println("first=" + i);
                }
//                break;
            } ;
        }


    }
}
