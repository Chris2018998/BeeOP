/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.RawObjectFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static cn.beeop.pool.PoolStaticCenter.*;
import static java.lang.System.currentTimeMillis;

/**
 * Pooled Entry
 *
 * @author Chris.Liao
 * @version 1.0
 */
final class PooledObject implements Cloneable {
    private static final ConcurrentHashMap<Object, Method> ObjectMethodMap = new ConcurrentHashMap<Object, Method>(16);
    private final ObjectPool pool;
    private final RawObjectFactory factory;
    private final Class[] objectInterfaces;
    private final Set<String> excludeMethodNames;
    private final boolean supportReflectProxy;

    Object raw;
    volatile int state;
    ObjectHandle handleInUsing;
    volatile long lastAccessTime;
    private Class rawClass;

    //***************************************************************************************************************//
    //                                  1: Pooled entry create/clone methods(2)                                      //                                                                                  //
    //***************************************************************************************************************//
    PooledObject(ObjectPool pool, RawObjectFactory factory, Class[] objectInterfaces, Set<String> excludeMethodNames) {
        this.pool = pool;
        this.factory = factory;
        this.objectInterfaces = objectInterfaces;
        this.excludeMethodNames = excludeMethodNames;

        if (objectInterfaces == null || objectInterfaces.length == 0) {
            supportReflectProxy = false;
        } else {
            Throwable cause = null;
            try {
                Proxy.newProxyInstance(
                        Pool_ClassLoader,
                        objectInterfaces,
                        new ObjectReflectHandler(this, null, excludeMethodNames));
            } catch (Throwable e) {
                cause = e;
            }
            supportReflectProxy = cause == null;
        }
    }

    final PooledObject setDefaultAndCopy(Object raw, int state) throws Exception {
        factory.setDefault(raw);
        PooledObject p = (PooledObject) clone();

        p.raw = raw;
        p.rawClass = raw.getClass();
        p.state = state;
        p.lastAccessTime = System.currentTimeMillis();//first time
        return p;
    }

    //***************************************************************************************************************//
    //                                  2: Pooled entry business methods(4)                                          //                                                                                  //
    //***************************************************************************************************************//
    public String toString() {
        return raw.toString();
    }

    final void updateAccessTime() {
        lastAccessTime = currentTimeMillis();
    }

    //called by pool before remove from pool
    void onBeforeRemove() {
        try {
            this.state = OBJECT_CLOSED;
        } catch (Throwable e) {
            CommonLog.error("Object close error", e);
        } finally {
            factory.destroy(raw);
        }
    }

    final void recycleSelf() throws ObjectException {
        try {
            handleInUsing = null;
            factory.reset(raw);
            pool.recycle(this);
        } catch (Throwable e) {
            pool.abandonOnReturn(this);
            throw e instanceof ObjectException ? (ObjectException) e : new ObjectException(e);
        }
    }


    //***************************************************************************************************************//
    //                                  3: reflect methods(2)                                                        //                                                                                  //
    //***************************************************************************************************************//
    final Object createReflectProxy(ObjectHandle handle) {
        return supportReflectProxy ? Proxy.newProxyInstance(
                Pool_ClassLoader,
                objectInterfaces,
                new ObjectReflectHandler(this, handle, excludeMethodNames)) : null;
    }

    final Object call(String name, Class[] types, Object[] params) throws ObjectException {
        if (excludeMethodNames.contains(name)) throw ObjectMethodForbiddenException;

        Object key = new MethodCacheKey(name, types);
        Method method = ObjectMethodMap.get(key);
        try {
            if (method == null) {
                method = rawClass.getMethod(name, types);
                Method mapMethod = ObjectMethodMap.putIfAbsent(key, method);
                if (mapMethod != null) method = mapMethod;
            }

            Object v = method.invoke(raw, params);
            updateAccessTime();
            return v;
        } catch (NoSuchMethodException e) {
            throw new ObjectException(e);
        } catch (IllegalAccessException e) {
            throw new ObjectException(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() != null) {
                throw new ObjectException(e.getCause());
            } else {
                throw new ObjectException(e);
            }
        }
    }

    //***************************************************************************************************************//
    //                                  4: inner class(1)                                                            //                                                                                  //
    //***************************************************************************************************************//
    private static final class MethodCacheKey {
        private final String name;
        private final Class[] types;

        MethodCacheKey(String name, Class[] types) {
            this.name = name;
            this.types = types;
        }

        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + Arrays.hashCode(types);
            return result;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodCacheKey that = (MethodCacheKey) o;
            return name.equals(that.name) &&
                    Arrays.equals(types, that.types);
        }
    }
}