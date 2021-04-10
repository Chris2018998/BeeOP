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

    BeeObjectHandle objectHandle;//chang when object borrowed out
    volatile long lastAccessTime;//read in pool
    private ObjectPool pool;
    private BeeObjectFactory objectFactory;

    public PooledEntry(Object rawObject, int state,
                       ObjectPool pool,
                       BeeObjectFactory objectFactory) {
        this.pool = pool;
        this.state = state;
        this.rawObject = rawObject;
        this.rawObjectClass = rawObject.getClass();
        this.objectFactory = objectFactory;
        this.lastAccessTime = currentTimeMillis();//first time
    }

    final void updateAccessTime() {
        lastAccessTime = currentTimeMillis();
    }

    public String toString() {
        return rawObject.toString();
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