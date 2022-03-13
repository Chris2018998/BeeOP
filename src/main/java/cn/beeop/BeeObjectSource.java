/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop;

import cn.beeop.pool.FastObjectPool;
import cn.beeop.pool.ObjectPool;
import cn.beeop.pool.ObjectPoolException;
import cn.beeop.pool.ObjectPoolMonitorVo;

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

    public BeeObjectSource(BeeObjectSourceConfig config) throws Exception {
        config.copyTo(this);
        createPool(this);
    }

    private static ObjectPool createPool(BeeObjectSource os) throws Exception {
        String poolImplementClassName = os.getPoolImplementClassName();
        if (isBlank(poolImplementClassName)) poolImplementClassName = FastObjectPool.class.getName();
        try {
            Class<?> poolClass = Class.forName(poolImplementClassName, true, Pool_ClassLoader);
            String errorMsg = checkClass(poolClass, ObjectPool.class, "pool");
            if (!isBlank(errorMsg)) throw new BeeObjectSourceConfigException(errorMsg);

            ObjectPool pool = (ObjectPool) poolClass.newInstance();
            pool.init(os);
            os.pool = pool;
            os.ready = true;
            return pool;
        } catch (ClassNotFoundException e) {
            throw new ObjectPoolException("Not found object pool class:" + poolImplementClassName);
        } catch (InstantiationException e) {
            throw new ObjectPoolException("Failed to instantiate object pool by class:" + poolImplementClassName, e);
        } catch (IllegalAccessException e) {
            throw new ObjectPoolException("Illegal access object pool class:" + poolImplementClassName, e);
        }
    }

    //***************************************************************************************************************//
    //                                        2: object take methods(1)                                              //
    //***************************************************************************************************************//
    public BeeObjectHandle getObject() throws Exception {
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
        this.clear(false);
    }

    public void clear(boolean force) throws Exception {
        if (pool == null) throw new ObjectPoolException("Object pool not initialized");
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
            }
        }
    }

    public void setPrintRuntimeLog(boolean printRuntimeLog) {
        if (pool != null) pool.setPrintRuntimeLog(printRuntimeLog);
    }

    public ObjectPoolMonitorVo getPoolMonitorVo() throws Exception {
        if (pool == null) throw new ObjectPoolException("Object pool not initialized");
        return pool.getPoolMonitorVo();
    }
}