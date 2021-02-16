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
package cn.beeop.pool;

import cn.beeop.BeeObjectException;
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
    ProxyObject getObject() throws BeeObjectException;

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
	
