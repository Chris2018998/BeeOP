/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

/**
 * Pool JMX Bean interface
 *
 * @author Chris.Liao
 * @version 1.0
 */
public interface ObjectPoolJmxBean {

    //return current size(using +idle)
    int getTotalSize();

    //return idle size
    int getIdleSize();

    //return using size
    int getUsingSize();

    //return permit size taken from semaphore
    int getSemaphoreAcquiredSize();

    //return waiting size to take semaphore permit
    int getSemaphoreWaitingSize();

    //return waiter size for transferred object
    int getTransferWaitingSize();

    //enable Runtime Log
    void setPrintRuntimeLog(boolean indicator);

}

