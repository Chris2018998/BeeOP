/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectHandle;

/**
 * object Handle factory
 *
 * @author Chris.Liao
 * @version 1.0
 */
class ObjectHandleFactory {

    BeeObjectHandle createHandle(PooledObject p, Borrower b) {
        b.lastUsed = p;
        return new ObjectHandle(p);
    }
}
