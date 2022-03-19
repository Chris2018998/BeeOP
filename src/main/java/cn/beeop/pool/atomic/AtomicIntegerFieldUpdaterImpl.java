/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool.atomic;

import sun.misc.Unsafe;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Atomic Integer Field Updater Implementation(Don't use in other place)
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class AtomicIntegerFieldUpdaterImpl<T> extends AtomicIntegerFieldUpdater<T> {
    private static final Unsafe unsafe = AtomicUnsafeUtil.getUnsafe();
    private final long offset;

    private AtomicIntegerFieldUpdaterImpl(long offset) {
        this.offset = offset;
    }

    public static <U> AtomicIntegerFieldUpdater<U> newUpdater(Class<U> beanClass, String fieldName) {
        try {
            return new AtomicIntegerFieldUpdaterImpl<U>(AtomicIntegerFieldUpdaterImpl.unsafe.objectFieldOffset(beanClass.getDeclaredField(fieldName)));
        } catch (Throwable e) {
            return AtomicIntegerFieldUpdater.newUpdater(beanClass, fieldName);
        }
    }

    public final boolean compareAndSet(T bean, int expect, int update) {
        return AtomicIntegerFieldUpdaterImpl.unsafe.compareAndSwapInt(bean, this.offset, expect, update);
    }

    public final boolean weakCompareAndSet(T bean, int expect, int update) {
        return AtomicIntegerFieldUpdaterImpl.unsafe.compareAndSwapInt(bean, this.offset, expect, update);
    }

    public final void set(T bean, int newValue) {
        AtomicIntegerFieldUpdaterImpl.unsafe.putIntVolatile(bean, this.offset, newValue);
    }

    public final void lazySet(T bean, int newValue) {
        AtomicIntegerFieldUpdaterImpl.unsafe.putOrderedInt(bean, this.offset, newValue);
    }

    public final int get(T bean) {
        return AtomicIntegerFieldUpdaterImpl.unsafe.getIntVolatile(bean, this.offset);
    }
}