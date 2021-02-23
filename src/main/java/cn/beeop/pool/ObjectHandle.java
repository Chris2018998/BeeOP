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
import cn.beeop.BeeObjectHandle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static cn.beeop.pool.StaticCenter.*;

/**
 * object Handle implement
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class ObjectHandle implements BeeObjectHandle {
    private PooledEntry pEntry;//owner
    private boolean isClosed;//handle state

    private Object rawObject;
    private Class rawObjectClass;
    private Object proxyReflectObject;
    private List<String> excludeMethodNames;

    public ObjectHandle(PooledEntry pEntry, List<String> excludeMethodNames) {
        this.pEntry = pEntry;
        this.rawObject = pEntry.rawObject;
        this.rawObjectClass = pEntry.rawObjectClass;
        this.excludeMethodNames = excludeMethodNames;
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

    public final Object getProxyObject() throws BeeObjectException {
        if (isClosed) throw new BeeObjectException();
        return proxyReflectObject;
    }

    void setProxyObject(Object proxyReflectObject) {
        this.proxyReflectObject = proxyReflectObject;
    }

    public final Object call(String name, Class[] types, Object[] params) throws BeeObjectException {
        try {
            if (isClosed) throw ObjectClosedException;
            if (excludeMethodNames.contains(name)) throw ObjectMethodForbiddenException;

            Object key = genMethodCacheKey(name, types);
            Method method = ObjectMethodMap.get(key);
            if (method == null) {
                method = rawObjectClass.getMethod(name, types);
                Method mapMethod = ObjectMethodMap.putIfAbsent(key, method);
                if (mapMethod != null) method = mapMethod;
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
