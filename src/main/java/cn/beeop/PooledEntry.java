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

import java.sql.SQLException;

import static java.lang.System.currentTimeMillis;

/**
 * Pooled Connection
 *
 * @author Chris.Liao
 * @version 1.0
 */
class PooledEntry {
    private static final boolean[] FALSE_ARRAY = new boolean[6];
    volatile int state;
    Object rawConn;
    ProxyObject proxyConn;
    volatile long lastAccessTime;
    volatile ObjectPool pool;
    PoolConfig config;

    public PooledEntry(Object rawConn, int connState, ObjectPool connPool, PoolConfig config) throws ObjectException {
        pool = connPool;
        state = connState;
        this.rawConn = rawConn;
        this.config = config;
        lastAccessTime = currentTimeMillis();//first time
    }

    final void recycleSelf() throws SQLException {
        try {
            proxyConn = null;
            // resetRawConnOnReturn();
            pool.recycle(this);
        } catch (Exception e) {
            pool.abandonOnReturn(this);
            throw e;
        }
    }
}