/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.RawObjectFactory;

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
    private static final ConcurrentHashMap<Object, Method> MethodMap = new ConcurrentHashMap<Object, Method>(16);
    private final ObjectPool pool;
    private final RawObjectFactory factory;
    private final Class[] objectInterfaces;
    private final Set<String> excludeMethodNames;

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
    }

    PooledObject setDefaultAndCopy(Object raw, int state) throws Exception {
        this.factory.setDefault(raw);
        PooledObject p = (PooledObject) this.clone();

        p.raw = raw;
        p.rawClass = raw.getClass();
        p.state = state;
        p.lastAccessTime = currentTimeMillis();//first time
        return p;
    }

    //***************************************************************************************************************//
    //                               2: Pooled entry business methods(4)                                             //                                                                                  //
    //***************************************************************************************************************//
    public String toString() {
        return this.raw.toString();
    }

    void updateAccessTime() {
        this.lastAccessTime = currentTimeMillis();
    }

    //called by pool before remove from pool
    void onBeforeRemove() {
        try {
            state = OBJECT_CLOSED;
        } catch (Throwable e) {
            CommonLog.error("Object close error", e);
        } finally {
            this.factory.destroy(this.raw);
        }
    }

    void recycleSelf() throws Exception {
        try {
            this.handleInUsing = null;
            this.factory.reset(this.raw);
            this.pool.recycle(this);
        } catch (Throwable e) {
            this.pool.abandonOnReturn(this);
            throw e;
        }
    }

    //***************************************************************************************************************//
    //                                  3: reflect methods(2)                                                        //                                                                                  //
    //***************************************************************************************************************//
    final Object createObjectProxy(ObjectHandle handle) {
        return Proxy.newProxyInstance(
                PoolClassLoader,
                objectInterfaces,
                new ObjectReflectHandler(this, handle, excludeMethodNames));
    }

    final Object call(String name, Class[] types, Object[] params) throws Exception {
        if (excludeMethodNames.contains(name)) throw ObjectMethodForbiddenException;

        Object key = new MethodCacheKey(name, types);
        Method method = MethodMap.get(key);

        if (method == null) {
            method = rawClass.getMethod(name, types);
            MethodMap.put(key, method);
        }

        Object v = method.invoke(raw, params);
        this.updateAccessTime();
        return v;
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
            int result = this.name.hashCode();
            result = 31 * result + Arrays.hashCode(this.types);
            return result;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || this.getClass() != o.getClass()) return false;
            MethodCacheKey that = (MethodCacheKey) o;
            return this.name.equals(that.name) &&
                    Arrays.equals(this.types, that.types);
        }
    }
}