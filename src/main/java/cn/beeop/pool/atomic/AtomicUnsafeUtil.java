/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool.atomic;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

/**
 * Atomic Unsafe Util
 *
 * @author Chris.Liao
 * @version 1.0
 */
class AtomicUnsafeUtil {
    private static Unsafe unsafe;

    static Unsafe getUnsafe() throws Exception {
        if (unsafe != null) return unsafe;
        synchronized (AtomicUnsafeUtil.class) {
            if (unsafe != null) return unsafe;
            unsafe = AccessController.doPrivileged(new PrivilegedExceptionAction<Unsafe>() {
                public Unsafe run() throws Exception {
                    Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafe.setAccessible(true);
                    return (Unsafe) theUnsafe.get(null);
                }
            });
            return unsafe;
        }
    }
}
