/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectHandle;
import cn.beeop.pool.exception.ObjectException;

import static cn.beeop.pool.PoolStaticCenter.*;

/**
 * object Handle implement
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class ObjectHandle implements BeeObjectHandle {
    private final PooledObject p;
    private boolean isClosed;
    private Object reflectProxy;
    private boolean reflectProxyCreated;

    ObjectHandle(PooledObject p) {
        this.p = p;
        p.handleInUsing = this;
    }

    //***************************************************************************************************************//
    //                                  1: override methods(4)                                                       //                                                                                  //
    //***************************************************************************************************************//
    public String toString() {
        return p.toString();
    }

    public boolean isClosed() {
        return isClosed;
    }

    public void close() throws ObjectException {
        synchronized (this) {//safe close
            if (isClosed) return;
            isClosed = true;
        }
        p.recycleSelf();
    }

    public synchronized Object getReflectProxy() throws Exception {
        if (isClosed) throw ObjectClosedException;
        if (!reflectProxyCreated) {
            reflectProxy = p.createReflectProxy(this);
            reflectProxyCreated = true;
        }
        return reflectProxy;
    }

    //***************************************************************************************************************//
    //                                 2: raw methods call methods(2)                                                //                                                                                  //
    //***************************************************************************************************************//
    public Object call(String methodName) throws Exception {
        return this.call(methodName, EmptyParamTypes, EmptyParamValues);
    }

    public Object call(String name, Class[] types, Object[] params) throws Exception {
        if (isClosed) throw ObjectClosedException;
        return p.call(name, types, params);
    }
}
