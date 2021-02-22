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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Proxy reflect handle
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class ProxyHandler implements InvocationHandler {
    private static final String Method_Close = "close";
    private static final String Method_isClosed = "isClosed";

    private Object rawObject;
    private BeeObjectHandle objectHandle;
    private PooledEntry pEntry;

    public ProxyHandler(PooledEntry pEntry, ObjectHandle objectHandle) {
        this.pEntry = pEntry;
        this.rawObject = rawObject;
        this.objectHandle = objectHandle;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (objectHandle.isClosed()) throw new BeeObjectException();

        if (method.getName().equals(Method_Close)) {
            objectHandle.close();
            return null;
        } else if (method.getName().equals(Method_Close)) {
            return objectHandle.isClosed();
        } else {
            Object v = method.invoke(rawObject, args);
            pEntry.updateAccessTime();
            return v;
        }
    }
}

