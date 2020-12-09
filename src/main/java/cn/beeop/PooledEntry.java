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

import static java.lang.System.currentTimeMillis;

/**
 * Pooled Entry
 *
 * @author Chris.Liao
 * @version 1.0
 */
class PooledEntry {
    volatile int state;
    Object object;
    ProxyObject proxyObject;

    private ObjectFactory factory;
    volatile long lastAccessTime;
    private ObjectPool pool;
    private PoolConfig config;

    public PooledEntry(Object object, int state, ObjectPool pool, PoolConfig config) throws ObjectException {
        this.pool = pool;
        this.state = state;
        this.object = object;
        this.config = config;
        this.factory = config.getObjectFactory();
        this.lastAccessTime = currentTimeMillis();//first time
    }

    final void updateAccessTime() {//for update,insert.select,delete and so on DML
        lastAccessTime = currentTimeMillis();
    }

    final void recycleSelf() throws ObjectException {
        try {
            proxyObject = null;
            factory.reset(object);
            pool.recycle(this);
        } catch (Exception e) {
            pool.abandonOnReturn(this);
            throw e;
        }
    }
}