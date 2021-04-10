/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU General Public License version 3.0.
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectHandle;
import cn.beeop.BeeObjectSourceConfig;

/**
 * object pool interface
 *
 * @author Chris.Liao
 * @version 1.0
 */
public interface ObjectPool {

    /**
     * initialize pool with configuration
     *
     * @param config data source configuration
     * @throws BeeObjectException check configuration fail or to create initiated objects
     */
    void init(BeeObjectSourceConfig config) throws BeeObjectException;

    /**
     * borrow a object from pool
     *
     * @return If exists idle object in pool,then return one;if not, waiting until other borrower release
     * @throws BeeObjectException if pool is closed or waiting timeout,then throw exception
     */
    BeeObjectHandle getObject() throws BeeObjectException;

    /**
     * return object to pool after used
     *
     * @param pConn release object to pool
     */
    void recycle(PooledEntry pConn);

    /**
     * remove failed object
     *
     * @param pConn release object to pool
     */
    void abandonOnReturn(PooledEntry pConn);

    /**
     * close pool
     *
     * @throws BeeObjectException if fail to close
     */
    void close() throws BeeObjectException;

    /**
     * check pool is closed
     *
     * @return true, closed, false active
     */
    boolean isClosed();

    /**
     * @return Pool Monitor Vo
     */
    PoolMonitorVo getMonitorVo();

    /**
     * Clear all objects from pool
     */
    public void clearAllObjects();

    /**
     * Clear all objects from pool
     *
     * @param forceCloseUsingOnClear close using objects directly
     */
    public void clearAllObjects(boolean forceCloseUsingOnClear);

}
	
