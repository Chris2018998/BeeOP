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

import java.lang.reflect.Proxy;

/**
 * Proxy reflect handler
 *
 * @author Chris.Liao
 * @version 1.0
 */

class ProxyObjectHandlerFactory extends ProxyObjectFactory {
    private ClassLoader classLoader;
    private Class[] objectInterfaces;

    public ProxyObjectHandlerFactory(Class[] objectInterfaces) {
        this.objectInterfaces = objectInterfaces;
        this.classLoader = this.getClass().getClassLoader();
    }

    public ProxyObject createObjectProxy(PooledEntry pEntry, Borrower borrower) throws BeeObjectException {
        borrower.lastUsedEntry = pEntry;
        return (ProxyObject) Proxy.newProxyInstance(
                classLoader,
                objectInterfaces,
                new ProxyObjectHandler(pEntry));
    }
}
