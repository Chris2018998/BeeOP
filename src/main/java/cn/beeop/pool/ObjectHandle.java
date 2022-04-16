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
public class ObjectHandle implements BeeObjectHandle {
    protected final PooledObject p;
    protected boolean isClosed;

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

    public final void close() throws Exception {
        synchronized (this) {//safe close
            if (isClosed) return;
            isClosed = true;
        }
        p.recycleSelf();
    }

    //***************************************************************************************************************//
    //                                 2: raw methods call methods(2)                                                //                                                                                  //
    //***************************************************************************************************************//
    public Object getObjectProxy() throws Exception {
        return null;
    }

    public Object call(String methodName) throws Exception {
        return call(methodName, EMPTY_CLASSES, EMPTY_CLASS_NAMES);
    }

    public Object call(String name, Class[] types, Object[] params) throws Exception {
        if (isClosed) throw ObjectClosedException;
        return p.call(name, types, params);
    }
}
