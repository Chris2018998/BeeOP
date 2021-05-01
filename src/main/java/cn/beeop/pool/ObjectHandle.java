/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU General Public License version 3.0.
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectHandle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import static cn.beeop.pool.StaticCenter.*;

/**
 * object Handle implement
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class ObjectHandle implements BeeObjectHandle {
    private PooledEntry pEntry;//owner
    private boolean isClosed;//handle state
    private Object rawObject;
    private Class rawObjectClass;
    private Object proxyReflectObject;
    private Set<String> excludeMethodNames;

    public ObjectHandle(PooledEntry pEntry, Set<String> excludeMethodNames) {
        this.pEntry = pEntry;
        this.rawObject = pEntry.rawObject;
        this.rawObjectClass = pEntry.rawObjectClass;
        this.excludeMethodNames = excludeMethodNames;
    }

    final void setAsClosed() {
        synchronized (this) {//safe close
            if (isClosed) return;
            isClosed = true;
        }
    }

    public String toString() {
        return pEntry.toString();
    }

    public boolean isClosed() throws BeeObjectException {
        return isClosed;
    }

    public final void close() throws BeeObjectException {
        setAsClosed();
        pEntry.recycleSelf();
    }

    public final Object getProxyObject() throws BeeObjectException {
        if (isClosed) throw new BeeObjectException();
        return proxyReflectObject;
    }

    void setProxyObject(Object proxyReflectObject) {
        this.proxyReflectObject = proxyReflectObject;
    }

    public Object call(String methodName) throws BeeObjectException {
        return call(methodName, EmptyParamTypes, EmptyParamValues);
    }

    public final Object call(String name, Class[] types, Object[] params) throws BeeObjectException {
        try {
            if (isClosed) throw ObjectClosedException;
            if (excludeMethodNames.contains(name)) throw ObjectMethodForbiddenException;

            Object key = genMethodCacheKey(name, types);
            Method method = ObjectMethodMap.get(key);
            if (method == null) {
                method = rawObjectClass.getMethod(name, types);
                Method mapMethod = ObjectMethodMap.putIfAbsent(key, method);
                if (mapMethod != null) method = mapMethod;
            }

            Object v = method.invoke(rawObject, params);
            pEntry.updateAccessTime();
            return v;
        } catch (NoSuchMethodException e) {
            throw new BeeObjectException(e);
        } catch (IllegalAccessException e) {
            throw new BeeObjectException(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() != null) {
                throw new BeeObjectException(e.getCause());
            } else {
                throw new BeeObjectException(e);
            }
        }
    }
}
