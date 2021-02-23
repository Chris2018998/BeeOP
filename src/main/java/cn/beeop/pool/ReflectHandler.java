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

import cn.beeop.BeeObjectHandle;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;

import static cn.beeop.pool.StaticCenter.ObjectClosedException;
import static cn.beeop.pool.StaticCenter.ObjectMethodForbiddenException;

/**
 * Proxy reflect handler
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class ReflectHandler implements InvocationHandler {
    private PooledEntry pEntry;
    private Object rawObject;
    private BeeObjectHandle objectHandle;//owner
    private List<String> excludeMethodNames;

    public ReflectHandler(PooledEntry pEntry, BeeObjectHandle objectHandle, List<String> excludeMethodNames) {
        this.pEntry = pEntry;
        this.rawObject = pEntry.rawObject;

        this.objectHandle = objectHandle;
        this.excludeMethodNames = excludeMethodNames;
    }

    public java.lang.Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (objectHandle.isClosed()) throw ObjectClosedException;
        if (excludeMethodNames.contains(method.getName())) throw ObjectMethodForbiddenException;

        Object v = method.invoke(rawObject, args);
        pEntry.updateAccessTime();
        return v;
    }
}

