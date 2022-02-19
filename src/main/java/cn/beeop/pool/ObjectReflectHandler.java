/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectHandle;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Set;

import static cn.beeop.pool.PoolStaticCenter.ObjectClosedException;
import static cn.beeop.pool.PoolStaticCenter.ObjectMethodForbiddenException;

/**
 * Proxy reflect handler
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class ObjectReflectHandler implements InvocationHandler {
    private final Object raw;
    private final PooledObject p;
    private final BeeObjectHandle owner;//owner
    private final Set<String> excludeMethodNames;

    ObjectReflectHandler(PooledObject p, BeeObjectHandle handle, Set<String> excludeMethodNames) {
        this.p = p;
        this.raw = p.raw;
        this.owner = handle;
        this.excludeMethodNames = excludeMethodNames;
    }

    //reflect method
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (owner.isClosed()) throw ObjectClosedException;
        if (excludeMethodNames.contains(method.getName())) throw ObjectMethodForbiddenException;

        Object v = method.invoke(raw, args);
        p.updateAccessTime();
        return v;
    }
}
