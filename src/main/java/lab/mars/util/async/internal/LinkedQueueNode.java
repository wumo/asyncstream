package lab.mars.util.async.internal;

import static lab.mars.util.async.internal.UnsafeAccess.UNSAFE;

final class LinkedQueueNode<E> {
    private final static long NEXT_OFFSET;
    static {
        try {
            NEXT_OFFSET = UNSAFE.objectFieldOffset(LinkedQueueNode.class.getDeclaredField("next"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private E value;
    private volatile LinkedQueueNode<E> next;

    LinkedQueueNode() {
        this(null);
    }

    LinkedQueueNode(E val) {
        storePrimitiveValue(val);
    }

    /**
     * Gets the current value and nulls out the reference to it from this node.
     * 
     * @return value
     */
    public E getAndNullValue() {
        E temp = loadPrimitiveValue();
        storePrimitiveValue(null);
        return temp;
    }

    public E loadPrimitiveValue() {
        return value;
    }

    public void storePrimitiveValue(E newValue) {
        value =  newValue;
    }

    public void soNext(LinkedQueueNode<E> n) {
        UNSAFE.putOrderedObject(this, NEXT_OFFSET, n);
    }

    public LinkedQueueNode<E> loadVolatileNext() {
        return next;
    }
}