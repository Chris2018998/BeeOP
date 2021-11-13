/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectHandle;

import java.util.Set;

/**
 * Proxy reflect handler factory
 *
 * @author Chris.Liao
 * @version 1.0
 */
interface ProxyObjectFactory {

    //create object proxy
    Object create(PooledEntry pEntry, BeeObjectHandle objectHandle, Set<String> excludeMethodNames) throws Exception;

}