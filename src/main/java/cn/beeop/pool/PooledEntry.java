/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectFactory;

import static cn.beeop.pool.PoolStaticCenter.OBJECT_CLOSED;
import static cn.beeop.pool.PoolStaticCenter.commonLog;
import static java.lang.System.currentTimeMillis;

/**
 * Pooled Entry
 *
 * @author Chris.Liao
 * @version 1.0
 */
final class PooledEntry {
    public Object rawObject;
    public Class rawObjectClass;
    public volatile int state;

    public ObjectHandle objectHandle;//chang when object borrowed out
    public volatile long lastAccessTime;//read in pool
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

    //called by pool before remove from pool
    void onBeforeRemove() {
        try {
            this.state = OBJECT_CLOSED;
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
            if (e instanceof BeeObjectException) {
                throw (BeeObjectException) e;
            } else {
                throw new BeeObjectException(e);
            }
        }
    }
}