/*
 * Copyright Chris2018998
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.beeop;

import cn.beeop.pool.ObjectPool;
import cn.beeop.pool.PoolMonitorVo;
import cn.beeop.pool.ProxyObject;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static cn.beeop.pool.StaticCenter.commonLog;
import static cn.beeop.pool.StaticCenter.isBlank;

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
    private boolean inited;
    private ObjectPool pool;
    private BeeObjectException failedCause;
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public BeeObjectSource() {
    }

    public BeeObjectSource(final BeeObjectSourceConfig config) throws BeeObjectException {
        config.copyTo(this);
        pool = createPool(this);
        inited = true;
    }

    /**
     * borrow a object from pool
     *
     * @return If exists idle object in pool,then return one;if not, waiting
     * until other borrower release
     * @throws BeeObjectException if pool is closed or waiting timeout,then throw exception
     */
    public ProxyObject getObject() throws BeeObjectException {
        if (inited) return pool.getObject();

        if (writeLock.tryLock()) {
            try {
                if (!inited) {
                    failedCause = null;
                    pool = createPool(this);
                    inited = true;
                }
            } catch (Throwable e) {//why?
                failedCause=(e instanceof BeeObjectException)?(BeeObjectException)e:new BeeObjectException(e);
                throw failedCause;
            } finally {
                writeLock.unlock();
            }
        } else {
            try {
                readLock.lock();
                if (failedCause != null) throw failedCause;
            } finally {
                readLock.unlock();
            }
        }

        return pool.getObject();
    }

    public void close() {
        if (pool != null) {
            try {
                pool.close();
            } catch (BeeObjectException e) {
                commonLog.error("Error on closing pool,cause:", e);
            }
        }
    }

    public boolean isClosed() {
        return (pool != null) ? pool.isClosed() : true;
    }

    /**
     * @return pool monitor vo
     * @throws BeeObjectException if pool not be initialized
     */
    public PoolMonitorVo getPoolMonitorVo() throws BeeObjectException {
        if (pool == null) throw new BeeObjectException("Object pool not initialized");
        return pool.getMonitorVo();
    }

    /**
     * clear all pooled objects from pool
     *
     * @throws BeeObjectException if pool under datasource not be initialized
     */
    public void clearAllObjects() throws BeeObjectException {
        this.clearAllObjects(false);
    }

    /**
     * clear all pooled objects from pool
     *
     * @param force close using object directly
     * @throws BeeObjectException if pool under object source not be initialized
     */
    public void clearAllObjects(boolean force) throws BeeObjectException {
        if (pool == null) throw new BeeObjectException("Object pool not initialized");
        pool.clearAllObjects(force);
    }

    //try to create pool instance by config
    private final ObjectPool createPool(BeeObjectSourceConfig config) throws BeeObjectException {
        String poolImplementClassName = config.getPoolImplementClassName();
        if (isBlank(poolImplementClassName)) poolImplementClassName = BeeObjectSourceConfig.DefaultImplementClassName;

        try {
            Class<?> poolClass = Class.forName(poolImplementClassName, true, getClass().getClassLoader());
            if (ObjectPool.class.isAssignableFrom(poolClass)) {
                ObjectPool pool = (ObjectPool) poolClass.newInstance();
                pool.init(config);
                return pool;
            } else {
                throw new BeeObjectSourceConfigException("poolImplementClassName error,must implement '" + ObjectPool.class.getName() + "' interface");
            }

        } catch (ClassNotFoundException e) {
            throw new BeeObjectSourceConfigException("Not found pool class:" + poolImplementClassName);
        } catch (InstantiationException e) {
            throw new BeeObjectSourceConfigException("Failed to instantiate pool class:" + poolImplementClassName, e);
        } catch (IllegalAccessException e) {
            throw new BeeObjectSourceConfigException("Failed to instantiate pool class:" + poolImplementClassName, e);
        }
    }
}
