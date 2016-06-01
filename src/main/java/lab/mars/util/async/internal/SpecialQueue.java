/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lab.mars.util.async.internal;

import static lab.mars.util.async.internal.UnsafeAccess.UNSAFE;

/**
 * <p>
 * Created by wumo on 2016/5/31.<br>
 * email: wumo@outlook.com
 * </P>
 * 为JCTools.MpscLinkedQueue添加一个push方法，使得单线程端可以以Stack的方式使用此Queue
 */
@sun.misc.Contended
@SuppressWarnings("unchecked")
public class SpecialQueue<E> {
    /**
     * consumerNode是一直存在的，其value为null，next标志可读的元素
     */
    public SpecialQueue() {
        consumerNode = new LinkedQueueNode<E>();
        xchgProducerNode(consumerNode);// this ensures correct construction: StoreLoad
    }

    /**
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Offer is allowed from multiple threads.<br>
     * Offer allocates a new node and:
     * <ol>
     * <li>Swaps it atomically with current producer node (only one producer 'wins')
     * <li>Sets the new node as the node following from the swapped producer node
     * </ol>
     * This works because each producer is guaranteed to 'plant' a new node and link the old node. No 2 producers can
     * get the same producer node as part of XCHG guarantee.
     */
    public final boolean offer(final E nextValue) {
        if (nextValue == null) {
            throw new IllegalArgumentException("null elements not allowed");
        }
        final LinkedQueueNode<E> nextNode = new LinkedQueueNode<>(nextValue);
        final LinkedQueueNode<E> prevProducerNode = xchgProducerNode(nextNode);
        // Should a producer thread get interrupted here the chain WILL be broken until that thread is resumed
        // and completes the store in prev.next.
        prevProducerNode.soNext(nextNode); // StoreStore
        /*这里prevProducerNode在执行soNext(nextNode)之前，它的next一定是null（因
         *为它是producerNode），而新加的node的next也是null。如果同时存在多个线程调用offer
         * 方法，会由xchgProducerNode方法保证按顺序添加（即正确设置各自的next属性）。
         * 这里设置next属性是使用soNext(lazySet)方法，由于是多个写线程，所以读线程中可能
         * 会发生不一致的情形（即consumerNode的next属性在写线程中已经设置了下一个node，
         * 而读线程中依然读的是null），此方法的使用将影响poll或peek的设计。
        */
        return true;
    }

    /**
     * <p>
     * 实现细节：<br>
     * push在consumerNode之前添加一个node，并将consumerNode修改为此新node<br>
     * <ol>
     * <li>push仅允许从单线程中调用
     * <li>push与poll等不能同时在不同的线程中调用
     * </ol>
     * </p>
     */
    public final void push(final E nextValue) {
        if (nextValue == null) {
            throw new IllegalArgumentException("null elements not allowed");
        }
        final LinkedQueueNode<E> nextNode = new LinkedQueueNode<>();
        nextNode.soNext(consumerNode);//StoreStore
        consumerNode.storePrimitiveValue(nextValue);
        consumerNode = nextNode;
    }

    /**
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Poll is allowed from a SINGLE thread.<br>
     * Poll reads the next node from the consumerNode and:
     * <ol>
     * <li>If it is null, the queue is assumed empty (though it might not be).
     * <li>If it is not null set it as the consumer node and return it's now evacuated value.
     * </ol>
     * This means the consumerNode.value is always null, which is also the starting point for the queue. Because null
     * values are not allowed to be offered this is the only node with it's value set to null at any one time.
     */
    public final E poll() {
        LinkedQueueNode<E> currConsumerNode = loadPrimitiveConsumerNode(); // don't load twice, it's alright
        LinkedQueueNode<E> nextNode = currConsumerNode.loadVolatileNext();
        if (nextNode != null) {
            // we have to null out the value because we are going to hang on to the node
            final E nextValue = nextNode.getAndNullValue();
            storePrimitiveConsumerNode(nextNode);
            return nextValue;
        } else if (currConsumerNode != loadVolatileProducerNode()) {
            // spin, we are no longer wait free
            while ((nextNode = currConsumerNode.loadVolatileNext()) == null) ;
            // got the next node...

            // we have to null out the value because we are going to hang on to the node
            final E nextValue = nextNode.getAndNullValue();
            consumerNode = nextNode;
            return nextValue;
        }
        return null;
    }

    public final E peek() {
        LinkedQueueNode<E> currConsumerNode = consumerNode; // don't load twice, it's alright
        LinkedQueueNode<E> nextNode = currConsumerNode.loadVolatileNext();
        if (nextNode != null) {
            return nextNode.loadPrimitiveValue();
        } else if (currConsumerNode != loadVolatileProducerNode()) {
            // spin, we are no longer wait free
            while ((nextNode = currConsumerNode.loadVolatileNext()) == null) ;
            // got the next node...
            return nextNode.loadPrimitiveValue();
        }
        return null;
    }

    public final boolean isEmpty() {
        return loadVotatileConsumerNode() == loadVolatileProducerNode();
    }

    public final boolean notEmpty() {
        return !isEmpty();
    }

    protected final static long P_NODE_OFFSET;
    protected final static long C_NODE_OFFSET;

    static {
        try {
            P_NODE_OFFSET = UNSAFE.objectFieldOffset(SpecialQueue.class.getDeclaredField("producerNode"));
            C_NODE_OFFSET = UNSAFE.objectFieldOffset(SpecialQueue.class.getDeclaredField("consumerNode"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    protected LinkedQueueNode<E> producerNode;
    protected LinkedQueueNode<E> consumerNode;

    protected final LinkedQueueNode<E> xchgProducerNode(LinkedQueueNode<E> newVal) {
        return (LinkedQueueNode<E>) UNSAFE.getAndSetObject(this, P_NODE_OFFSET, newVal);
    }

    protected final void storePrimitiveProducerNode(LinkedQueueNode<E> node) {
        producerNode = node;
    }

    @SuppressWarnings("unchecked")
    protected final LinkedQueueNode<E> loadVolatileProducerNode() {
        return (LinkedQueueNode<E>) UNSAFE.getObjectVolatile(this, P_NODE_OFFSET);
    }

    protected final LinkedQueueNode<E> loadPrimitiveProducerNode() {
        return producerNode;
    }

    protected final void storePrimitiveConsumerNode(LinkedQueueNode<E> node) {
        consumerNode = node;
    }

    @SuppressWarnings("unchecked")
    protected final LinkedQueueNode<E> loadVotatileConsumerNode() {
        return (LinkedQueueNode<E>) UNSAFE.getObjectVolatile(this, C_NODE_OFFSET);
    }

    protected final LinkedQueueNode<E> loadPrimitiveConsumerNode() {
        return consumerNode;
    }
}
