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
final class ObjectReflectHandler implements InvocationHandler, Cloneable {
    private final Set<String> excludeMethodNames;
    private Object raw;
    private PooledObject p;
    private BeeObjectHandle owner;

    ObjectReflectHandler(Set<String> excludeMethodNames) {
        this.excludeMethodNames = excludeMethodNames;
    }

    final ObjectReflectHandler copy(PooledObject p, BeeObjectHandle handle) throws Exception {
        ObjectReflectHandler handler = (ObjectReflectHandler) super.clone();
        handler.p = p;
        handler.raw = p.raw;
        handler.owner = handle;
        return handler;
    }

    //reflect method
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (owner.isClosed()) throw ObjectClosedException;
        if (excludeMethodNames.contains(method.getName())) throw ObjectMethodForbiddenException;

        Object v = method.invoke(this.raw, args);
        p.updateAccessTime();
        return v;
    }
}

