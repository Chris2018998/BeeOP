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

import static cn.beeop.pool.PoolStaticCenter.*;


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
            BeeObjectSource.createPool(this);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static ObjectPool createPool(BeeObjectSource os) throws Exception {
        Class<?> poolClass = Class.forName(os.getPoolImplementClassName());
        String errorMsg = checkClass(poolClass, ObjectPool.class, "pool");
        if (!isBlank(errorMsg)) throw new BeeObjectSourceConfigException(errorMsg);

        ObjectPool pool = (ObjectPool) poolClass.newInstance();
        pool.init(os);
        os.pool = pool;
        os.ready = true;
        return pool;
    }

    //***************************************************************************************************************//
    //                                        2: object take methods(1)                                              //
    //***************************************************************************************************************//
    public final BeeObjectHandle getObject() throws Exception {
        if (this.ready) return this.pool.getObject();
        synchronized (this.synLock) {
            if (this.pool != null) return this.pool.getObject();
            return BeeObjectSource.createPool(this).getObject();
        }
    }

    //***************************************************************************************************************//
    //                                          3: pool other methods(6)                                             //
    //***************************************************************************************************************//
    public void clear() throws Exception {
        clear(false);
    }

    public void clear(boolean force) throws Exception {
        if (this.pool == null) throw new PoolNotCreateException("Object pool not initialized");
        this.pool.clear(force);
    }

    public boolean isClosed() {
        return this.pool == null || this.pool.isClosed();
    }

    public void close() {
        if (this.pool != null) {
            try {
                this.pool.close();
            } catch (Throwable e) {
                CommonLog.error("Error at closing pool,cause:", e);
            }
        }
    }

    public void setPrintRuntimeLog(boolean printRuntimeLog) {
        if (this.pool != null) this.pool.setPrintRuntimeLog(printRuntimeLog);
    }

    public ObjectPoolMonitorVo getPoolMonitorVo() throws Exception {
        if (this.pool == null) throw new PoolNotCreateException("Object pool not initialized");
        return this.pool.getPoolMonitorVo();
    }
}
