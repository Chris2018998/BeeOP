/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import java.lang.reflect.Proxy;

import static cn.beeop.pool.PoolStaticCenter.ObjectClosedException;
import static cn.beeop.pool.PoolStaticCenter.PoolClassLoader;

/**
 * object Handle support proxy
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class ObjectHandleWithProxy extends ObjectHandle {
    private Object objectProxy;
    private boolean proxyCreated;

    ObjectHandleWithProxy(PooledObject p) {
        super(p);
    }

    public Object getObjectProxy() throws Exception {
        if (isClosed) throw ObjectClosedException;
        if (proxyCreated) return objectProxy;

        synchronized (this) {
            if (!proxyCreated) {
                try {
                    objectProxy = Proxy.newProxyInstance(
                            PoolClassLoader,
                            p.objectInterfaces,
                            new ObjectReflectHandler(p, this));
                } finally {
                    proxyCreated = true;
                }
            }
        }
        return objectProxy;
    }
}
