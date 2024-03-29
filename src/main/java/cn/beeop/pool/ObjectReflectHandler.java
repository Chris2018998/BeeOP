/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectHandle;
import cn.beeop.pool.exception.ObjectException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Set;


/**
 * Proxy reflect handler
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class ObjectReflectHandler implements InvocationHandler {
    private final Object raw;
    private final PooledObject p;
    private final BeeObjectHandle handle;
    private final Set<String> excludeMethodNames;

    ObjectReflectHandler(PooledObject p, ObjectHandle handle) {
        this.p = p;
        this.raw = p.raw;
        this.handle = handle;
        this.excludeMethodNames = p.excludeMethodNames;
    }

    //reflect method
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (handle.isClosed()) throw new ObjectException("No operations allowed after object handle closed");
        if (excludeMethodNames.contains(method.getName())) throw new ObjectException("Method illegal access");

        Object v = method.invoke(raw, args);
        p.updateAccessTime();
        return v;
    }
}

