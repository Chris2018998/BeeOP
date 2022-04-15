/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectHandle;

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
    private Object objectProxy;

    ObjectHandle(PooledObject p) {
        this.p = p;
        p.handleInUsing = this;
    }

    //***************************************************************************************************************//
    //                                  1: override methods(4)                                                       //                                                                                  //
    //***************************************************************************************************************//
    public String toString() {
        return this.p.toString();
    }

    public boolean isClosed() {
        return this.isClosed;
    }

    public final void close() throws Exception {
        synchronized (this) {//safe close
            if (this.isClosed) return;
            this.isClosed = true;
        }
        this.p.recycleSelf();
    }

    public final Object getObjectProxy() throws Exception {
        if (this.isClosed) throw ObjectClosedException;
        return this.objectProxy;
    }

    void setObjectProxy(Object objectProxy) {
        this.objectProxy = objectProxy;
    }

    //***************************************************************************************************************//
    //                                 2: raw methods call methods(2)                                                //                                                                                  //
    //***************************************************************************************************************//
    public Object call(String methodName) throws Exception {
        return call(methodName, EMPTY_CLASSES, EMPTY_CLASS_NAMES);
    }

    public Object call(String name, Class[] types, Object[] params) throws Exception {
        if (this.isClosed) throw ObjectClosedException;
        return this.p.call(name, types, params);
    }
}
