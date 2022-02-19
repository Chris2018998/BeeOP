/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool.atomic;

import sun.misc.Unsafe;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Atomic Reference Field Updater Implementation(Don't use in other place)
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class AtomicReferenceFieldUpdaterImpl<T, V> extends AtomicReferenceFieldUpdater<T, V> {
    private final static Unsafe unsafe = AtomicUnsafeUtil.getUnsafe();
    private final long offset;

    private AtomicReferenceFieldUpdaterImpl(long offset) {
        this.offset = offset;
    }

    public static <U, W> AtomicReferenceFieldUpdater<U, W> newUpdater(Class<U> beanClass, Class<W> fieldType, String fieldName) {
        try {
            return new AtomicReferenceFieldUpdaterImpl<U, W>(unsafe.objectFieldOffset(beanClass.getDeclaredField(fieldName)));
        } catch (Throwable e) {
            return AtomicReferenceFieldUpdater.newUpdater(beanClass, fieldType, fieldName);
        }
    }

    public final boolean compareAndSet(T bean, V expect, V update) {
        return unsafe.compareAndSwapObject(bean, offset, expect, update);
    }

    public final boolean weakCompareAndSet(T bean, V expect, V update) {
        return unsafe.compareAndSwapObject(bean, offset, expect, update);
    }

    public final void set(T bean, V newValue) {
        unsafe.putObjectVolatile(bean, offset, newValue);
    }

    public final void lazySet(T bean, V newValue) {
        unsafe.putOrderedObject(bean, offset, newValue);
    }

    public final V get(T bean) {
        return (V) unsafe.getObjectVolatile(bean, offset);
    }
}