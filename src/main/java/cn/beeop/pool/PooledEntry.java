/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU General Public License version 3.0.
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectFactory;
import cn.beeop.BeeObjectHandle;

import java.sql.SQLException;

import static cn.beeop.pool.StaticCenter.OBJECT_CLOSED;
import static cn.beeop.pool.StaticCenter.commonLog;
import static java.lang.System.currentTimeMillis;

/**
 * Pooled Entry
 *
 * @author Chris.Liao
 * @version 1.0
 */
class PooledEntry {
    Object rawObject;
    Class rawObjectClass;
    volatile int state;

    ObjectHandle objectHandle;//chang when object borrowed out
    volatile long lastAccessTime;//read in pool
    private ObjectPool pool;
    private BeeObjectFactory objectFactory;

    public PooledEntry(Object rawObject,
                       int state,
                       ObjectPool pool,
                       BeeObjectFactory objectFactory) {
        this.pool = pool;
        this.state = state;
        this.rawObject = rawObject;
        this.rawObjectClass = rawObject.getClass();
        this.objectFactory = objectFactory;
        this.lastAccessTime = currentTimeMillis();//first time
    }

    public String toString() {
        return rawObject.toString();
    }

    final void updateAccessTime() {
        lastAccessTime = currentTimeMillis();
    }

    //close raw connection
    void onBeforeRemove() {
        try {
            this.state = OBJECT_CLOSED;
            if (objectHandle != null) {
                objectHandle.setAsClosed();
                objectHandle = null;
            }
        } catch (Throwable e) {
            commonLog.error("Object close error", e);
        } finally {
            objectFactory.destroy(rawObject);
        }
    }

    final void recycleSelf() throws BeeObjectException {
        try {
            objectHandle = null;
            objectFactory.reset(rawObject);
            pool.recycle(this);
        } catch (Throwable e) {
            pool.abandonOnReturn(this);
            if (e instanceof SQLException) {
                throw (BeeObjectException) e;
            } else {
                throw new BeeObjectException(e);
            }
        }
    }
}