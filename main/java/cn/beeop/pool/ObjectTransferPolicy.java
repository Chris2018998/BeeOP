/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

/**
 * Pooled object transfer policy interface
 *
 * @author Chris.Liao
 * @version 1.0
 */
interface ObjectTransferPolicy {
    int getStateCodeOnRelease();

    void beforeTransfer(PooledObject p);

    boolean tryCatch(PooledObject p);

    void onTransferFail(PooledObject p);

}