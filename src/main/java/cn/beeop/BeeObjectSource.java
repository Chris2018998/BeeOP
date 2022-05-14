/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop;

import cn.beeop.pool.ObjectPool;
import cn.beeop.pool.ObjectPoolMonitorVo;
import cn.beeop.pool.exception.PoolNotCreateException;

import static cn.beeop.pool.PoolStaticCenter.CommonLog;
import static cn.beeop.pool.PoolStaticCenter.createClassInstance;

/**
 * Bee Object object source
 * <p>
 * Email:  Chris2018998@tom.com
 * Project: https://github.com/Chris2018998/beeop
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class BeeObjectSource extends BeeObjectSourceConfig {
    private final Object synLock = new Object();
    private volatile boolean ready;
    private ObjectPool pool;

    //***************************************************************************************************************//
    //                                             1:constructors(2)                                                 //
    //***************************************************************************************************************//
    public BeeObjectSource() {
    }

    public BeeObjectSource(BeeObjectSourceConfig config) {
        try {
            config.copyTo(this);
            createPool(this);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ObjectPool createPool(BeeObjectSource os) throws Exception {
        Class<?> poolClass = Class.forName(os.getPoolImplementClassName());
        ObjectPool pool = (ObjectPool) createClassInstance(poolClass, ObjectPool.class, "pool");

        pool.init(os);
        os.pool = pool;
        os.ready = true;
        return pool;
    }

    //***************************************************************************************************************//
    //                                        2: object take methods(1)                                              //
    //***************************************************************************************************************//
    public final BeeObjectHandle getObject() throws Exception {
        if (ready) return pool.getObject();
        synchronized (synLock) {
            if (pool != null) return pool.getObject();
            return createPool(this).getObject();
        }
    }

    //***************************************************************************************************************//
    //                                          3: pool other methods(6)                                             //
    //***************************************************************************************************************//
    public void clear() throws Exception {
        clear(false);
    }

    public void clear(boolean force) throws Exception {
        if (pool == null) throw new PoolNotCreateException("Object pool not initialized");
        pool.clear(force);
    }

    public boolean isClosed() {
        return pool == null || pool.isClosed();
    }

    public void close() {
        if (pool != null) {
            try {
                pool.close();
            } catch (Throwable e) {
                CommonLog.error("Error at closing pool,cause:", e);
            } finally {
                this.ready = !pool.isClosed();
            }
        }
    }

    public void setPrintRuntimeLog(boolean printRuntimeLog) {
        if (pool != null) pool.setPrintRuntimeLog(printRuntimeLog);
    }

    public ObjectPoolMonitorVo getPoolMonitorVo() throws Exception {
        if (pool == null) throw new PoolNotCreateException("Object pool not initialized");
        return pool.getPoolMonitorVo();
    }
}