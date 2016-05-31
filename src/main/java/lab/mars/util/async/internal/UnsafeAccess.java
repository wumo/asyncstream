package lab.mars.util.async.internal;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeAccess {
    public static final boolean SUPPORTS_GET_AND_SET;
    public static final Unsafe UNSAFE;
    static {
        try {
            final Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (Exception e) {
            SUPPORTS_GET_AND_SET = false;
            throw new RuntimeException(e);
        }
        boolean getAndSetSupport = false;
        try {
            Unsafe.class.getMethod("getAndSetObject", Object.class, Long.TYPE,Object.class);
            getAndSetSupport = true;
        } catch (Exception e) {
        }
        SUPPORTS_GET_AND_SET = getAndSetSupport;
    }

}