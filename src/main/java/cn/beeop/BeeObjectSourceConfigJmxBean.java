/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU General Public License version 3.0.
 */
package cn.beeop;

/**
 * Bee Object SourceConfig JMX Bean interface
 *
 * @author Chris.Liao
 * @version 1.0
 */

public interface BeeObjectSourceConfigJmxBean {

    String getPoolName();

    boolean isFairMode();

    int getInitialSize();

    int getMaxActive();

    int getBorrowSemaphoreSize();

    long getMaxWait();

    long getIdleTimeout();

    long getHoldTimeout();

    int getObjectTestTimeout();

    long getObjectTestInterval();

    boolean isForceCloseUsingOnClear();

    long getDelayTimeForNextClear();

    long getIdleCheckTimeInterval();

    String getPoolImplementClassName();

    String getObjectFactoryClassName();

    boolean isEnableJmx();
}
