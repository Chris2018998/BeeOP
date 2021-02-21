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
import cn.beeop.BeeObjectFactory;

import static java.lang.System.currentTimeMillis;

/**
 * Pooled Entry
 *
 * @author Chris.Liao
 * @version 1.0
 */
class PooledEntry {
    volatile int state;
    Object rawObject;
    Class rawObjectClass;
    ProxyHandle proxyHandle;

    volatile long lastAccessTime;
    private BeeObjectFactory objectFactory;
    private ObjectPool pool;

    public PooledEntry(Object object, int state, ObjectPool pool, BeeObjectFactory factory) {
        this.pool = pool;
        this.state = state;
        this.rawObject = object;
        this.rawObjectClass = object.getClass();
        this.objectFactory = factory;
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
            proxyHandle = null;
            objectFactory.reset(rawObject);
            pool.recycle(this);
        } catch (Exception e) {
            pool.abandonOnReturn(this);
            throw e;
        }
    }
}