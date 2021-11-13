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
public final class ProxyObjectHandler implements InvocationHandler {
    private PooledEntry pEntry;
    private Object rawObject;
    private BeeObjectHandle owner;//owner
    private Set<String> excludeMethodNames;

    public ProxyObjectHandler(PooledEntry pEntry, BeeObjectHandle objectHandle, Set<String> excludeMethodNames) {
        this.pEntry = pEntry;
        this.rawObject = pEntry.rawObject;

        this.owner = objectHandle;
        this.excludeMethodNames = excludeMethodNames;
    }

    public java.lang.Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (owner.isClosed()) throw ObjectClosedException;
        if (excludeMethodNames.contains(method.getName())) throw ObjectMethodForbiddenException;

        Object v = method.invoke(rawObject, args);
        pEntry.updateAccessTime();
        return v;
    }
}

