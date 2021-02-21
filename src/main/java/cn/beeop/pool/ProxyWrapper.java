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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static cn.beeop.pool.StaticCenter.ObjectMethodMap;
import static cn.beeop.pool.StaticCenter.genMethodCacheKey;

/**
 * object wrapper proxy
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class ProxyWrapper implements ProxyHandle {
    protected Object rawObject;
    protected PooledEntry pEntry;
    protected boolean isClosed;
    private Class rawObjectClass;

    public ProxyWrapper(PooledEntry pEntry) {
        this.pEntry = pEntry;
        pEntry.proxyHandle = this;
        this.rawObject = pEntry.rawObject;
        this.rawObjectClass = pEntry.rawObjectClass;
    }

    public String toString() {
        return pEntry.toString();
    }

    public boolean isClosed() throws BeeObjectException {
        return isClosed;
    }

    public final void close() throws BeeObjectException {
        synchronized (this) {//safe close
            if (isClosed) return;
            isClosed = true;
        }
        pEntry.recycleSelf();
    }

    public final Object call(String name, Class[] types, Object[] params) throws BeeObjectException {
        try {
            if (isClosed) throw new BeeObjectException();
            Object key = genMethodCacheKey(name, types);
            Method method = ObjectMethodMap.get(key);
            if (method == null) {
                method = rawObjectClass.getMethod(name, types);
                Method mapMethod=ObjectMethodMap.putIfAbsent(key, method);
                if(mapMethod!=null)method=mapMethod;
            }

            Object v = method.invoke(rawObject, params);
            pEntry.updateAccessTime();
            return v;
        } catch (NoSuchMethodException e) {
            throw new BeeObjectException(e);
        } catch (IllegalAccessException e) {
            throw new BeeObjectException(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() != null) {
                throw new BeeObjectException(e.getCause());
            } else {
                throw new BeeObjectException(e);
            }
        }
    }
}
