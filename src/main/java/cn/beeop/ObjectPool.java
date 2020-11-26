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
package cn.beeop;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static cn.beeop.StaticCenter.*;
import static java.lang.System.*;
import static java.lang.Thread.yield;
import static java.util.concurrent.TimeUnit.*;
import static java.util.concurrent.locks.LockSupport.*;

/**
 * Object Pool Implementation
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class ObjectPool {
    private static final long spinForTimeoutThreshold = 1000L;
    private static final int maxTimedSpins = (Runtime.getRuntime().availableProcessors() < 2) ? 0 : 32;
    private static final AtomicIntegerFieldUpdater<PooledEntry> ConnStUpd = AtomicIntegerFieldUpdater.newUpdater(PooledEntry.class, "state");
    private static final AtomicReferenceFieldUpdater<Borrower, Object> BwrStUpd = AtomicReferenceFieldUpdater.newUpdater(Borrower.class, Object.class, "state");
    private static final String DESC_REMOVE_INIT = "init";
    private static final String DESC_REMOVE_BAD = "bad";
    private static final String DESC_REMOVE_IDLE = "idle";
    private static final String DESC_REMOVE_CLOSED = "closed";
    private static final String DESC_REMOVE_RESET = "reset";
    private static final String DESC_REMOVE_DESTROY = "destroy";
    private static final AtomicInteger poolNameIndex = new AtomicInteger(1);
    private final Object connArrayLock = new Object();
    private final Object connNotifyLock = new Object();
    private final ThreadLocal<WeakReference<Borrower>> threadLocal = new ThreadLocal<WeakReference<Borrower>>();

    private int poolMaxSize;
    private long defaultMaxWaitNanos;//nanoseconds
    private int conUnCatchStateCode;
    private int connectionTestTimeout;//seconds
    private long connectionTestInterval;//milliseconds
    private ConnectionPoolHook exitHook;
    private PoolConfig poolConfig;
    private Semaphore borrowSemaphore;
    private ConcurrentLinkedQueue<Borrower> waitQueue = new ConcurrentLinkedQueue<Borrower>();

    private TransferPolicy transferPolicy;
    private ConnectionTestPolicy testPolicy;
    private ObjectFactory connFactory;
    private PooledConnAddThread connAddThread;
    private volatile PooledEntry[] connArray = new PooledEntry[0];
    private ScheduledFuture<?> idleCheckSchFuture;
    private ScheduledThreadPoolExecutor idleSchExecutor = new ScheduledThreadPoolExecutor(2, new PoolThreadThreadFactory("IdleConnectionScan"));
    private String poolName = "";
    private String poolMode = "";
    private AtomicInteger poolState = new AtomicInteger(POOL_UNINIT);
    private AtomicInteger createConnThreadState = new AtomicInteger(THREAD_WORKING);
    private AtomicInteger needAddConnSize = new AtomicInteger(0);

    /**
     * initialize pool with configuration
     *
     * @param config data source configuration
     * @throws SQLException check configuration fail or to create initiated connection
     */
    public void init(PoolConfig config) throws ObjectException {
        if (poolState.get() == POOL_UNINIT) {
            checkProxyClasses();
            if (config == null) throw new ObjectException("DataSource configuration can't be null");
            poolConfig = config;

            poolName = !isBlank(config.getPoolName()) ? config.getPoolName() : "FastPool-" + poolNameIndex.getAndIncrement();
            commonLog.info("beeop({})starting....", poolName);

            poolMaxSize = poolConfig.getMaxActive();
            connFactory = poolConfig.getObjectFactory();
            // connectionTestTimeout = poolConfig.getConnectionTestTimeout();

            defaultMaxWaitNanos = MILLISECONDS.toNanos(poolConfig.getMaxWait());
            // connectionTestInterval = poolConfig.getConnectionTestInterval();
            createInitConnections(poolConfig.getInitialSize());

            if (poolConfig.isFairMode()) {
                poolMode = "fair";
                transferPolicy = new FairTransferPolicy();
                conUnCatchStateCode = transferPolicy.getCheckStateCode();
            } else {
                poolMode = "compete";
                transferPolicy = new CompeteTransferPolicy();
                conUnCatchStateCode = transferPolicy.getCheckStateCode();
            }

            exitHook = new ConnectionPoolHook();
            Runtime.getRuntime().addShutdownHook(exitHook);

            borrowSemaphore = new Semaphore(poolConfig.getBorrowSemaphoreSize(), poolConfig.isFairMode());
            idleSchExecutor.setKeepAliveTime(15, SECONDS);
            idleSchExecutor.allowCoreThreadTimeOut(true);
            idleCheckSchFuture = idleSchExecutor.scheduleAtFixedRate(new Runnable() {
                public void run() {// check idle connection
                    closeIdleTimeoutConnection();
                }
            }, config.getIdleCheckTimeInitDelay(), config.getIdleCheckTimeInterval(), TimeUnit.MILLISECONDS);

            registerJMX();
            commonLog.info("beeop({})has startup{mode:{},init size:{},max size:{},semaphore size:{},max wait:{}ms}",
                    poolName,
                    poolMode,
                    connArray.length,
                    config.getMaxActive(),
                    poolConfig.getBorrowSemaphoreSize(),
                    poolConfig.getMaxWait()
            );

            poolState.set(POOL_NORMAL);

            connAddThread = new PooledConnAddThread();
            connAddThread.setDaemon(true);
            connAddThread.setName("PooledEntryAdd");
            connAddThread.start();
        } else {
            throw new ObjectException("Pool has initialized");
        }
    }

    /**
     * check some proxy classes whether exists
     */
    private void checkProxyClasses() throws ObjectException {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            Class.forName("cn.beeop.Borrower", true, classLoader);
            Class.forName("cn.beeop.pool.PooledEntry", true, classLoader);
            Class.forName("cn.beeop.pool.ProxyConnection", true, classLoader);
            Class.forName("cn.beeop.pool.ProxyStatement", true, classLoader);
            Class.forName("cn.beeop.pool.ProxyPsStatement", true, classLoader);
            Class.forName("cn.beeop.pool.ProxyCsStatement", true, classLoader);
            Class.forName("cn.beeop.pool.ProxyDatabaseMetaData", true, classLoader);
            Class.forName("cn.beeop.pool.ProxyResultSet", true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new ObjectException("Jdbc proxy classes missed", e);
        }
    }

    ThreadPoolExecutor getNetworkTimeoutExecutor() {
        return idleSchExecutor;
    }

    private boolean existBorrower() {
        return poolConfig.getBorrowSemaphoreSize() > borrowSemaphore.availablePermits() || borrowSemaphore.hasQueuedThreads();
    }

    //create Pooled connection
    private PooledEntry createPooledConn(int connState) throws ObjectException {
        synchronized (connArrayLock) {
            int arrayLen = connArray.length;
            if (arrayLen < poolMaxSize) {
                commonLog.debug("beeop({}))begin to create new pooled connection,state:{}", poolName, connState);
                Object con = connFactory.create();
                setDefaultOnRawConn(con);
                PooledEntry pConn = new PooledEntry(con, connState, this, poolConfig);// add
                commonLog.debug("beeop({}))has created new pooled connection:{},state:{}", poolName, pConn, connState);
                PooledEntry[] arrayNew = new PooledEntry[arrayLen + 1];
                arraycopy(connArray, 0, arrayNew, 0, arrayLen);
                arrayNew[arrayLen] = pConn;// tail
                connArray = arrayNew;
                return pConn;
            } else {
                return null;
            }
        }
    }

    //remove Pooled connection
    private void removePooledConn(PooledEntry pConn, String removeType) {
        commonLog.debug("beeop({}))begin to remove pooled connection:{},reason:{}", poolName, pConn, removeType);
        pConn.state = CONNECTION_CLOSED;
        // pConn.closeRawConn();
        synchronized (connArrayLock) {
            int oldLen = connArray.length;
            PooledEntry[] arrayNew = new PooledEntry[oldLen - 1];
            for (int i = 0; i < oldLen; i++) {
                if (connArray[i] == pConn) {
                    arraycopy(connArray, 0, arrayNew, 0, i);
                    int m = oldLen - i - 1;
                    if (m > 0) arraycopy(connArray, i + 1, arrayNew, i, m);
                    break;
                }
            }

            commonLog.debug("beeop({}))has removed pooled connection:{},reason:{}", poolName, pConn, removeType);
            connArray = arrayNew;
        }
    }

    //set default attribute on raw connection
    private void setDefaultOnRawConn(Object rawConn) {

    }

    /**
     * check connection state
     *
     * @return if the checked connection is active then return true,otherwise
     * false if false then close it
     */
    private final boolean testOnBorrow(PooledEntry pConn) {
        if (currentTimeMillis() - pConn.lastAccessTime - connectionTestInterval < 0 || testPolicy.isActive(pConn))
            return true;

        removePooledConn(pConn, DESC_REMOVE_BAD);
        tryToCreateNewConnByAsyn();
        return false;
    }

    /**
     * create initialization connections
     *
     * @throws SQLException error occurred in creating connections
     */
    private void createInitConnections(int initSize) throws ObjectException {
        try {
            for (int i = 0; i < initSize; i++)
                createPooledConn(CONNECTION_IDLE);
        } catch (ObjectException e) {
            for (PooledEntry pConn : connArray)
                removePooledConn(pConn, DESC_REMOVE_INIT);
            throw e;
        }
    }

    /**
     * borrow one connection from pool
     *
     * @return If exists idle connection in pool,then return one;if not, waiting
     * until other borrower release
     * @throws SQLException if pool is closed or waiting timeout,then throw exception
     */
    public Object getConnection() throws ObjectException {
        if (poolState.get() == POOL_NORMAL) {
            //0:try to get from threadLocal cache
            WeakReference<Borrower> ref = threadLocal.get();
            Borrower borrower = (ref != null) ? ref.get() : null;
            if (borrower != null) {
                PooledEntry pConn = borrower.lastUsedEntry;
                if (pConn != null && ConnStUpd.compareAndSet(pConn, CONNECTION_IDLE, CONNECTION_USING)) {
                    if (testOnBorrow(pConn)) return null; //return createProxyConnection(pConn, borrower);

                    borrower.lastUsedEntry = null;
                }
            } else {
                borrower = new Borrower();
                threadLocal.set(new WeakReference<Borrower>(borrower));
            }


            final long deadline = nanoTime() + defaultMaxWaitNanos;
            try {
                if (!borrowSemaphore.tryAcquire(this.defaultMaxWaitNanos, NANOSECONDS))
                    throw RequestTimeoutException;
            } catch (InterruptedException e) {
                throw RequestInterruptException;
            }

            try {//borrowSemaphore acquired
                //1:try to search one from array
                PooledEntry[] tempArray = connArray;
                int len = tempArray.length;
                PooledEntry pConn;
                for (int i = 0; i < len; i++) {
                    pConn = tempArray[i];
                    if (ConnStUpd.compareAndSet(pConn, CONNECTION_IDLE, CONNECTION_USING) && testOnBorrow(pConn))
                        return null;
                    // return createProxyConnection(pConn, borrower);
                }

                //2:try to create one directly
                if (connArray.length < poolMaxSize && (pConn = createPooledConn(CONNECTION_USING)) != null)
                    return null;
                // return createProxyConnection(pConn, borrower);

                //3:try to get one transferred connection
                boolean failed = false;
                ObjectException failedCause = null;
                borrower.state = BORROWER_NORMAL;
                waitQueue.offer(borrower);
                int spinSize = (waitQueue.peek() == borrower) ? maxTimedSpins : 0;

                while (true) {
                    Object state = borrower.state;
                    if (state instanceof PooledEntry) {
                        pConn = (PooledEntry) state;
                        if (transferPolicy.tryCatch(pConn) && testOnBorrow(pConn)) {
                            waitQueue.remove(borrower);
                            return null;
                            //return createProxyConnection(pConn, borrower);
                        }

                        state = BORROWER_NORMAL;
                        borrower.state = state;
                        yield();
                    } else if (state instanceof ObjectException) {
                        waitQueue.remove(borrower);
                        throw (ObjectException) state;
                    }

                    if (failed) {
                        BwrStUpd.compareAndSet(borrower, state, failedCause);
                    } else {
                        long timeout = deadline - nanoTime();
                        if (timeout > 0L) {
                            if (spinSize > 0) {
                                spinSize--;
                            } else if (timeout - spinForTimeoutThreshold > 0 && BwrStUpd.compareAndSet(borrower, state, BORROWER_WAITING)) {
                                parkNanos(borrower, timeout);
                                BwrStUpd.compareAndSet(borrower, BORROWER_WAITING, BORROWER_NORMAL);//reset to normal
                                if (borrower.thread.isInterrupted()) {
                                    failed = true;
                                    failedCause = RequestInterruptException;
                                }
                            }
                        } else {//timeout
                            failed = true;
                            failedCause = RequestTimeoutException;
                        }
                    }
                }//while
            } finally {
                borrowSemaphore.release();
            }
        } else {
            throw PoolCloseException;
        }
    }

    /**
     * remove connection
     *
     * @param pConn target connection need release
     */
    void abandonOnReturn(PooledEntry pConn) {
        removePooledConn(pConn, DESC_REMOVE_BAD);
        tryToCreateNewConnByAsyn();
    }

    /**
     * return connection to pool
     *
     * @param pConn target connection need release
     */
    public final void recycle(PooledEntry pConn) {
        transferPolicy.beforeTransfer(pConn);

        for (Borrower borrower : waitQueue)
            for (Object state = borrower.state; state == BORROWER_NORMAL || state == BORROWER_WAITING; state = borrower.state) {
                if (pConn.state - this.conUnCatchStateCode != 0) return;
                if (BwrStUpd.compareAndSet(borrower, state, pConn)) {
                    if (state == BORROWER_WAITING)
                        unpark(borrower.thread);
                    return;
                }
            }
        transferPolicy.onFailedTransfer(pConn);
    }

    /**
     * @param exception: transfer Exception to waiter
     */
    private void transferException(ObjectException exception) {
        for (Borrower borrower : waitQueue)
            for (Object state = borrower.state; state == BORROWER_NORMAL || state == BORROWER_WAITING; state = borrower.state) {
                if (BwrStUpd.compareAndSet(borrower, state, exception)) {//transfer successful
                    if (state == BORROWER_WAITING) unpark(borrower.thread);
                    return;
                }
            }
    }

    /**
     * inner timer will call the method to clear some idle timeout connections
     * or dead connections,or long time not active connections in using state
     */
    private void closeIdleTimeoutConnection() {
        if (poolState.get() == POOL_NORMAL) {
            PooledEntry[] array = connArray;
            for (int i = 0, len = array.length; i < len; i++) {
                PooledEntry pConn = array[i];
                int state = pConn.state;
                if (state == CONNECTION_IDLE && !existBorrower()) {
                    boolean isTimeoutInIdle = (currentTimeMillis() - pConn.lastAccessTime - poolConfig.getIdleTimeout() >= 0);
                    if (isTimeoutInIdle && ConnStUpd.compareAndSet(pConn, state, CONNECTION_CLOSED)) {//need close idle
                        removePooledConn(pConn, DESC_REMOVE_IDLE);
                        tryToCreateNewConnByAsyn();
                    }
                } else if (state == CONNECTION_USING) {
                    ProxyObject proxyConn = pConn.proxyConn;
                    boolean isHoldTimeoutInNotUsing = currentTimeMillis() - pConn.lastAccessTime - poolConfig.getHoldTimeout() >= 0;
                    if (isHoldTimeoutInNotUsing && proxyConn != null) {//recycle connection
                        proxyConn.trySetAsClosed();
                    }
                } else if (state == CONNECTION_CLOSED) {
                    removePooledConn(pConn, DESC_REMOVE_CLOSED);
                    tryToCreateNewConnByAsyn();
                }
            }
            //commonLog.debug("beeop({})idle:{},using:{},semaphore-waiter:{},wait-transfer:{}", poolName, vo.getIdleSize(), vo.getUsingSize(), vo.getSemaphoreWaiterSize(), vo.getTransferWaiterSize());
        }
    }

    // shutdown pool
    public void close() throws ObjectException {
        long parkNanoSeconds = SECONDS.toNanos(poolConfig.getWaitTimeToClearPool());
        while (true) {
            if (poolState.compareAndSet(POOL_NORMAL, POOL_CLOSED)) {
                commonLog.info("beeop({})begin to shutdown", poolName);
                removeAllConnections(poolConfig.isForceCloseConnection(), DESC_REMOVE_DESTROY);
                unregisterJMX();
                shutdownCreateConnThread();
                while (!idleCheckSchFuture.isCancelled() && !idleCheckSchFuture.isDone())
                    idleCheckSchFuture.cancel(true);
                idleSchExecutor.shutdownNow();
                try {
                    Runtime.getRuntime().removeShutdownHook(exitHook);
                } catch (Throwable e) {
                }

                commonLog.info("beeop({})has shutdown", poolName);
                break;
            } else if (poolState.get() == POOL_CLOSED) {
                break;
            } else {
                parkNanos(parkNanoSeconds);// wait 3 seconds
            }
        }
    }

    public boolean isClosed() {
        return poolState.get() == POOL_CLOSED;
    }

    // remove all connections
    private void removeAllConnections(boolean force, String source) {
        while (existBorrower()) {
            transferException(PoolCloseException);
        }

        long parkNanoSeconds = SECONDS.toNanos(poolConfig.getWaitTimeToClearPool());
        while (connArray.length > 0) {
            PooledEntry[] array = connArray;
            for (int i = 0, len = array.length; i < len; i++) {
                PooledEntry pConn = array[i];
                if (ConnStUpd.compareAndSet(pConn, CONNECTION_IDLE, CONNECTION_CLOSED)) {
                    removePooledConn(pConn, source);
                } else if (pConn.state == CONNECTION_CLOSED) {
                    removePooledConn(pConn, source);
                } else if (pConn.state == CONNECTION_USING) {
                    ProxyObject proxyConn = pConn.proxyConn;
                    if (force) {
                        if (proxyConn != null) {
                            proxyConn.trySetAsClosed();
                        }
                    } else {
                        boolean isTimeout = (currentTimeMillis() - pConn.lastAccessTime - poolConfig.getHoldTimeout() >= 0);
                        if (isTimeout && proxyConn != null) {
                            proxyConn.trySetAsClosed();
                        }
                    }
                }
            } // for

            if (connArray.length > 0) parkNanos(parkNanoSeconds);
        } // while
        idleSchExecutor.getQueue().clear();
    }

    // notify to create connections to pool
    private void tryToCreateNewConnByAsyn() {
        if (connArray.length + needAddConnSize.get() < poolMaxSize) {
            synchronized (connNotifyLock) {
                if (connArray.length + needAddConnSize.get() < poolMaxSize) {
                    needAddConnSize.incrementAndGet();
                    if (createConnThreadState.compareAndSet(THREAD_WAITING, THREAD_WORKING))
                        unpark(connAddThread);
                }
            }
        }
    }

    // exit connection creation thread
    private void shutdownCreateConnThread() {
        int curSts;
        while (true) {
            curSts = createConnThreadState.get();
            if ((curSts == THREAD_WORKING || curSts == THREAD_WAITING) && createConnThreadState.compareAndSet(curSts, THREAD_DEAD)) {
                if (curSts == THREAD_WAITING) unpark(connAddThread);
                break;
            }
        }
    }

    /******************************** JMX **************************************/
    // close all connections
    public void reset() {
        reset(false);// wait borrower release connection,then close them
    }

    // close all connections
    public void reset(boolean force) {
        if (poolState.compareAndSet(POOL_NORMAL, POOL_RESTING)) {
            commonLog.info("beeop({})begin to reset.", poolName);
            removeAllConnections(force, DESC_REMOVE_RESET);
            commonLog.info("All pooledConn were cleared");
            poolState.set(POOL_NORMAL);// restore state;
            commonLog.info("beeop({})finished resetting", poolName);
        }
    }

    public int getConnTotalSize() {
        return connArray.length;
    }

    public int getConnIdleSize() {
        int idleConnections = 0;
        for (PooledEntry pConn : this.connArray) {
            if (pConn.state == CONNECTION_IDLE)
                idleConnections++;
        }
        return idleConnections;
    }

    public int getConnUsingSize() {
        int active = connArray.length - getConnIdleSize();
        return (active > 0) ? active : 0;
    }

    public int getSemaphoreAcquiredSize() {
        return poolConfig.getBorrowSemaphoreSize() - borrowSemaphore.availablePermits();
    }

    public int getSemaphoreWaitingSize() {
        return borrowSemaphore.getQueueLength();
    }

    public int getTransferWaitingSize() {
        int size = 0;
        for (Borrower borrower : waitQueue) {
            Object state = borrower.state;
            if (state == BORROWER_NORMAL || state == BORROWER_WAITING)
                size++;
        }
        return size;
    }

//    public ConnectionPoolMonitorVo getMonitorVo() {
//        int totSize = getConnTotalSize();
//        int idleSize = getConnIdleSize();
//        monitorVo.setPoolName(poolName);
//        monitorVo.setPoolMode(poolMode);
//        monitorVo.setPoolState(poolState.get());
//        monitorVo.setMaxActive(poolMaxSize);
//        monitorVo.setIdleSize(idleSize);
//        monitorVo.setUsingSize(totSize - idleSize);
//        monitorVo.setSemaphoreWaiterSize(getSemaphoreWaitingSize());
//        monitorVo.setTransferWaiterSize(getTransferWaitingSize());
//        return monitorVo;
//    }

    // register JMX
    private void registerJMX() {
        if (poolConfig.isEnableJMX()) {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            registerJMXBean(mBeanServer, String.format("cn.beeop.pool.FastConnectionPool:type=beeop(%s)", poolName), this);
            registerJMXBean(mBeanServer, String.format("cn.beeop.ObjectPoolConfig:type=beeop(%s)-config", poolName), poolConfig);
        }
    }

    private void registerJMXBean(MBeanServer mBeanServer, String regName, Object bean) {
        try {
            ObjectName jmxRegName = new ObjectName(regName);
            if (!mBeanServer.isRegistered(jmxRegName)) {
                mBeanServer.registerMBean(bean, jmxRegName);
            }
        } catch (Exception e) {
            commonLog.warn("beeop({})failed to register jmx-bean:{}", poolName, regName, e);
        }
    }

    // unregister JMX
    private void unregisterJMX() {
        if (poolConfig.isEnableJMX()) {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            unregisterJMXBean(mBeanServer, String.format("cn.beeop.pool.FastConnectionPool:type=beeop(%s)", poolName));
            unregisterJMXBean(mBeanServer, String.format("cn.beeop.ObjectPoolConfig:type=beeop(%s)-config", poolName));
        }
    }

    private void unregisterJMXBean(MBeanServer mBeanServer, String regName) {
        try {
            ObjectName jmxRegName = new ObjectName(regName);
            if (mBeanServer.isRegistered(jmxRegName)) {
                mBeanServer.unregisterMBean(jmxRegName);
            }
        } catch (Exception e) {
            commonLog.warn("beeop({})failed to unregister jmx-bean:{}", poolName, regName, e);
        }
    }

    // Connection check Policy
    static interface ConnectionTestPolicy {
        boolean isActive(PooledEntry pConn);
    }

    // Transfer Policy
    static interface TransferPolicy {
        int getCheckStateCode();

        void beforeTransfer(PooledEntry pConn);

        boolean tryCatch(PooledEntry pConn);

        void onFailedTransfer(PooledEntry pConn);
    }

    static final class PoolThreadThreadFactory implements ThreadFactory {
        private String thName;

        public PoolThreadThreadFactory(String thName) {
            this.thName = thName;
        }

        public Thread newThread(Runnable r) {
            Thread th = new Thread(r, thName);
            th.setDaemon(true);
            return th;
        }
    }

    //******************************** JMX **************************************/
    static final class CompeteTransferPolicy implements TransferPolicy {
        public final int getCheckStateCode() {
            return CONNECTION_IDLE;
        }

        public final boolean tryCatch(PooledEntry pConn) {
            return ConnStUpd.compareAndSet(pConn, CONNECTION_IDLE, CONNECTION_USING);
        }

        public final void onFailedTransfer(PooledEntry pConn) {
        }

        public final void beforeTransfer(PooledEntry pConn) {
            pConn.state = CONNECTION_IDLE;
        }
    }

    static final class FairTransferPolicy implements TransferPolicy {
        public final int getCheckStateCode() {
            return CONNECTION_USING;
        }

        public final boolean tryCatch(PooledEntry pConn) {
            return pConn.state == CONNECTION_USING;
        }

        public final void onFailedTransfer(PooledEntry pConn) {
            pConn.state = CONNECTION_IDLE;
        }

        public final void beforeTransfer(PooledEntry pConn) {
        }
    }

    // create connection to pool
    private class PooledConnAddThread extends Thread {
        public void run() {
            PooledEntry pConn;
            while (true) {
                while (needAddConnSize.get() > 0) {
                    needAddConnSize.decrementAndGet();
                    if (getTransferWaitingSize() > 0) {
                        try {
                            if ((pConn = createPooledConn(CONNECTION_USING)) != null)
                                recycle(pConn);
                        } catch (ObjectException e) {
                            transferException(e);
                        }
                    }
                }

                if (needAddConnSize.get() == 0 && createConnThreadState.compareAndSet(THREAD_WORKING, THREAD_WAITING))
                    park(this);
                if (createConnThreadState.get() == THREAD_DEAD) break;
            }
        }
    }

    /**
     * Hook when JVM exit
     */
    private class ConnectionPoolHook extends Thread {
        public void run() {
            try {
                ObjectPool.this.close();
            } catch (ObjectException e) {
                commonLog.error("Error on closing connection pool,cause:", e);
            }
        }
    }
}
