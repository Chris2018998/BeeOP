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
import java.util.Properties;
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
public final class ObjectPool implements ObjectPoolJmx {
    private static final long spinForTimeoutThreshold = 1000L;
    private static final int maxTimedSpins = (Runtime.getRuntime().availableProcessors() < 2) ? 0 : 32;
    private static final AtomicIntegerFieldUpdater<PooledEntry> ObjStUpd = AtomicIntegerFieldUpdater.newUpdater(PooledEntry.class, "state");
    private static final AtomicReferenceFieldUpdater<Borrower, Object> BwrStUpd = AtomicReferenceFieldUpdater.newUpdater(Borrower.class, Object.class, "state");
    private static final String DESC_REMOVE_INIT = "init";
    private static final String DESC_REMOVE_BAD = "bad";
    private static final String DESC_REMOVE_IDLE = "idle";
    private static final String DESC_REMOVE_CLOSED = "closed";
    private static final String DESC_REMOVE_RESET = "reset";
    private static final String DESC_REMOVE_DESTROY = "destroy";
    private static final AtomicInteger poolNameIndex = new AtomicInteger(1);
    private final Object entryArrayLock = new Object();
    private final Object entryAddNotifyLock = new Object();
    private final PoolMonitorVo monitorVo = new PoolMonitorVo();
    private final ThreadLocal<WeakReference<Borrower>> threadLocal = new ThreadLocal<WeakReference<Borrower>>();

    private String poolName = "";
    private String poolMode = "";
    private int poolMaxSize;
    private long defaultMaxWaitNanos;//nanoseconds
    private int entryUnCatchStateCode;
    private int objectTestTimeout;//seconds
    private long objectTestInterval;//milliseconds
    private Properties createProperties;

    private PoolHook exitHook;
    private PoolConfig poolConfig;
    private Semaphore borrowSemaphore;
    private TransferPolicy transferPolicy;
    private ConcurrentLinkedQueue<Borrower> waitQueue = new ConcurrentLinkedQueue<Borrower>();

    private ObjectFactory objectFactory;
    private PooledConnAddThread poolEntryAddThread;
    private volatile PooledEntry[] poolEntryArray = new PooledEntry[0];
    private ScheduledFuture<?> idleCheckSchFuture;
    private ScheduledThreadPoolExecutor idleSchExecutor = new ScheduledThreadPoolExecutor(2, new PoolThreadThreadFactory("IdleConnectionScan"));
    private AtomicInteger poolState = new AtomicInteger(POOL_UNINIT);
    private AtomicInteger createObjThreadState = new AtomicInteger(THREAD_WORKING);
    private AtomicInteger needAddConnSize = new AtomicInteger(0);

    /**
     * initialize pool with configuration
     *
     * @param config data source configuration
     * @throws ObjectException check configuration fail or to create initiated object
     */
    public ObjectPool(PoolConfig config) throws ObjectException {
        if (poolState.get() == POOL_UNINIT) {
            if (config == null) throw new ObjectException("Pool configuration can't be null");
            poolConfig = config;

            poolName = !isBlank(config.getPoolName()) ? config.getPoolName() : "FastPool-" + poolNameIndex.getAndIncrement();
            commonLog.info("BeeOP({})starting....", poolName);

            poolMaxSize = poolConfig.getMaxActive();
            objectFactory = poolConfig.getObjectFactory();
            objectTestTimeout = poolConfig.getAliveTestTimeout();
            objectTestInterval = poolConfig.getAliveTestInterval();
            defaultMaxWaitNanos = MILLISECONDS.toNanos(poolConfig.getMaxWait());
            createProperties = poolConfig.getCreateProperties();

            createInitObjects(poolConfig.getInitialSize());

            if (poolConfig.isFairMode()) {
                poolMode = "fair";
                transferPolicy = new FairTransferPolicy();
                entryUnCatchStateCode = transferPolicy.getCheckStateCode();
            } else {
                poolMode = "compete";
                transferPolicy = new CompeteTransferPolicy();
                entryUnCatchStateCode = transferPolicy.getCheckStateCode();
            }

            exitHook = new PoolHook();
            Runtime.getRuntime().addShutdownHook(exitHook);

            borrowSemaphore = new Semaphore(poolConfig.getBorrowSemaphoreSize(), poolConfig.isFairMode());
            idleSchExecutor.setKeepAliveTime(15, SECONDS);
            idleSchExecutor.allowCoreThreadTimeOut(true);
            idleCheckSchFuture = idleSchExecutor.scheduleAtFixedRate(new Runnable() {
                                                                         public void run() {// check idle object
                                                                             closeIdleTimeoutConnection();
                                                                         }
                                                                     }, 1000,
                    config.getIdleCheckTimeInterval(),
                    TimeUnit.MILLISECONDS);

            registerJMX();
            commonLog.info("BeeOP({})has startup{mode:{},init size:{},max size:{},semaphore size:{},max wait:{}ms}",
                    poolName,
                    poolMode,
                    poolEntryArray.length,
                    config.getMaxActive(),
                    poolConfig.getBorrowSemaphoreSize(),
                    poolConfig.getMaxWait()
            );

            poolState.set(POOL_NORMAL);

            poolEntryAddThread = new PooledConnAddThread();
            poolEntryAddThread.setDaemon(true);
            poolEntryAddThread.setName("PooledEntryAdd");
            poolEntryAddThread.start();
        } else {
            throw new ObjectException("Pool has initialized");
        }
    }

    private boolean existBorrower() {
        return poolConfig.getBorrowSemaphoreSize() > borrowSemaphore.availablePermits() || borrowSemaphore.hasQueuedThreads();
    }

    //create Pooled object
    private PooledEntry createPooledEntry(int connState) throws ObjectException {
        synchronized (entryArrayLock) {
            int arrayLen = poolEntryArray.length;
            if (arrayLen < poolMaxSize) {
                commonLog.debug("BeeOP({}))begin to create new pooled object,state:{}", poolName, connState);
                Object obj = objectFactory.create(createProperties);
                objectFactory.setDefault(obj);
                PooledEntry pConn = new PooledEntry(obj, connState, this, poolConfig);// add
                commonLog.debug("BeeOP({}))has created new pooled object:{},state:{}", poolName, pConn, connState);
                PooledEntry[] arrayNew = new PooledEntry[arrayLen + 1];
                arraycopy(poolEntryArray, 0, arrayNew, 0, arrayLen);
                arrayNew[arrayLen] = pConn;// tail
                poolEntryArray = arrayNew;
                return pConn;
            } else {
                return null;
            }
        }
    }

    //remove Pooled object
    private void removePooledEntry(PooledEntry pEntry, String removeType) {
        commonLog.debug("BeeOP({}))begin to remove pooled object:{},reason:{}", poolName, pEntry, removeType);
        pEntry.state = OBJECT_CLOSED;
        // pConn.closeRawConn();
        synchronized (entryArrayLock) {
            int oldLen = poolEntryArray.length;
            PooledEntry[] arrayNew = new PooledEntry[oldLen - 1];
            for (int i = 0; i < oldLen; i++) {
                if (poolEntryArray[i] == pEntry) {
                    arraycopy(poolEntryArray, 0, arrayNew, 0, i);
                    int m = oldLen - i - 1;
                    if (m > 0) arraycopy(poolEntryArray, i + 1, arrayNew, i, m);
                    break;
                }
            }

            objectFactory.destroy(pEntry.object);
            commonLog.debug("BeeOP({}))has removed pooled object:{},reason:{}", poolName, pEntry, removeType);
            poolEntryArray = arrayNew;
        }
    }


    /**
     * check object state
     *
     * @return if the checked object is active then return true,otherwise
     * false if false then close it
     */
    private final boolean testOnBorrow(PooledEntry pEntry) {
        if (currentTimeMillis() - pEntry.lastAccessTime - objectTestInterval < 0 || objectFactory.isAlive(pEntry.object, poolConfig.getAliveTestTimeout()))
            return true;

        removePooledEntry(pEntry, DESC_REMOVE_BAD);
        tryToCreateNewConnByAsyn();
        return false;
    }

    /**
     * create initialization objects
     *
     * @throws ObjectException error occurred in creating objects
     */
    private void createInitObjects(int initSize) throws ObjectException {
        try {
            for (int i = 0; i < initSize; i++)
                createPooledEntry(OBJECT_IDLE);
        } catch (ObjectException e) {
            for (PooledEntry pConn : poolEntryArray)
                removePooledEntry(pConn, DESC_REMOVE_INIT);
            throw e;
        }
    }

    /**
     * borrow one object from pool
     *
     * @return If exists idle object in pool,then return one;if not, waiting
     * until other borrower release
     * @throws ObjectException if pool is closed or waiting timeout,then throw exception
     */
    public Object getConnection() throws ObjectException {
        if (poolState.get() == POOL_NORMAL) {
            //0:try to get from threadLocal cache
            WeakReference<Borrower> ref = threadLocal.get();
            Borrower borrower = (ref != null) ? ref.get() : null;
            if (borrower != null) {
                PooledEntry pConn = borrower.lastUsedEntry;
                if (pConn != null && ObjStUpd.compareAndSet(pConn, OBJECT_IDLE, OBJECT_USING)) {
                    if (testOnBorrow(pConn)) {
                        borrower.lastUsedEntry = pConn;
                        return new ProxyObject(pConn);
                    }
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
                PooledEntry[] tempArray = poolEntryArray;
                int len = tempArray.length;
                PooledEntry pConn;
                for (int i = 0; i < len; i++) {
                    pConn = tempArray[i];
                    if (ObjStUpd.compareAndSet(pConn, OBJECT_IDLE, OBJECT_USING) && testOnBorrow(pConn)) {
                        borrower.lastUsedEntry = pConn;
                        return new ProxyObject(pConn);
                    }
                }

                //2:try to create one directly
                if (poolEntryArray.length < poolMaxSize && (pConn = createPooledEntry(OBJECT_USING)) != null) {
                    borrower.lastUsedEntry = pConn;
                    return new ProxyObject(pConn);
                }

                //3:try to get one transferred object
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
                            borrower.lastUsedEntry = pConn;
                            return new ProxyObject(pConn);
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
     * remove object
     *
     * @param pConn target object need release
     */
    void abandonOnReturn(PooledEntry pConn) {
        removePooledEntry(pConn, DESC_REMOVE_BAD);
        tryToCreateNewConnByAsyn();
    }

    /**
     * return object to pool
     *
     * @param pConn target object need release
     */
    public final void recycle(PooledEntry pConn) {
        transferPolicy.beforeTransfer(pConn);

        for (Borrower borrower : waitQueue)
            for (Object state = borrower.state; state == BORROWER_NORMAL || state == BORROWER_WAITING; state = borrower.state) {
                if (pConn.state - this.entryUnCatchStateCode != 0) return;
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
     * inner timer will call the method to clear some idle timeout objects
     * or dead objects,or long time not active objects in using state
     */
    private void closeIdleTimeoutConnection() {
        if (poolState.get() == POOL_NORMAL) {
            PooledEntry[] array = poolEntryArray;
            for (int i = 0, len = array.length; i < len; i++) {
                PooledEntry pConn = array[i];
                int state = pConn.state;
                if (state == OBJECT_IDLE && !existBorrower()) {
                    boolean isTimeoutInIdle = (currentTimeMillis() - pConn.lastAccessTime - poolConfig.getIdleTimeout() >= 0);
                    if (isTimeoutInIdle && ObjStUpd.compareAndSet(pConn, state, OBJECT_CLOSED)) {//need close idle
                        removePooledEntry(pConn, DESC_REMOVE_IDLE);
                        tryToCreateNewConnByAsyn();
                    }
                } else if (state == OBJECT_USING) {
                    ProxyObject proxyConn = pConn.proxyConn;
                    boolean isHoldTimeoutInNotUsing = currentTimeMillis() - pConn.lastAccessTime - poolConfig.getHoldTimeout() >= 0;
                    if (isHoldTimeoutInNotUsing && proxyConn != null) {//recycle object
                        proxyConn.trySetAsClosed();
                    }
                } else if (state == OBJECT_CLOSED) {
                    removePooledEntry(pConn, DESC_REMOVE_CLOSED);
                    tryToCreateNewConnByAsyn();
                }
            }

            PoolMonitorVo vo = this.getMonitorVo();
            commonLog.debug("BeeOP({})idle:{},using:{},semaphore-waiter:{},wait-transfer:{}", poolName, vo.getIdleSize(), vo.getUsingSize(), vo.getSemaphoreWaiterSize(), vo.getTransferWaiterSize());
        }
    }

    // shutdown pool
    public void close() throws ObjectException {
        long parkNanoSeconds = SECONDS.toNanos(poolConfig.getWaitTimeToClearPool());
        while (true) {
            if (poolState.compareAndSet(POOL_NORMAL, POOL_CLOSED)) {
                commonLog.info("BeeOP({})begin to shutdown", poolName);
                removeAllConnections(poolConfig.isForceClose(), DESC_REMOVE_DESTROY);
                unregisterJMX();
                shutdownCreateConnThread();
                while (!idleCheckSchFuture.isCancelled() && !idleCheckSchFuture.isDone())
                    idleCheckSchFuture.cancel(true);
                idleSchExecutor.shutdownNow();
                try {
                    Runtime.getRuntime().removeShutdownHook(exitHook);
                } catch (Throwable e) {
                }

                commonLog.info("BeeOP({})has shutdown", poolName);
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

    // remove all objects
    private void removeAllConnections(boolean force, String source) {
        while (existBorrower()) {
            transferException(PoolCloseException);
        }

        long parkNanoSeconds = SECONDS.toNanos(poolConfig.getWaitTimeToClearPool());
        while (poolEntryArray.length > 0) {
            PooledEntry[] array = poolEntryArray;
            for (int i = 0, len = array.length; i < len; i++) {
                PooledEntry pConn = array[i];
                if (ObjStUpd.compareAndSet(pConn, OBJECT_IDLE, OBJECT_CLOSED)) {
                    removePooledEntry(pConn, source);
                } else if (pConn.state == OBJECT_CLOSED) {
                    removePooledEntry(pConn, source);
                } else if (pConn.state == OBJECT_USING) {
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

            if (poolEntryArray.length > 0) parkNanos(parkNanoSeconds);
        } // while
        idleSchExecutor.getQueue().clear();
    }

    // notify to create objects to pool
    private void tryToCreateNewConnByAsyn() {
        if (poolEntryArray.length + needAddConnSize.get() < poolMaxSize) {
            synchronized (entryAddNotifyLock) {
                if (poolEntryArray.length + needAddConnSize.get() < poolMaxSize) {
                    needAddConnSize.incrementAndGet();
                    if (createObjThreadState.compareAndSet(THREAD_WAITING, THREAD_WORKING))
                        unpark(poolEntryAddThread);
                }
            }
        }
    }

    // exit objects creation thread
    private void shutdownCreateConnThread() {
        int curSts;
        while (true) {
            curSts = createObjThreadState.get();
            if ((curSts == THREAD_WORKING || curSts == THREAD_WAITING) && createObjThreadState.compareAndSet(curSts, THREAD_DEAD)) {
                if (curSts == THREAD_WAITING) unpark(poolEntryAddThread);
                break;
            }
        }
    }

    /******************************** JMX **************************************/
    // close all objects
    public void reset() {
        reset(false);// wait borrower release objects,then close them
    }

    // close all objects
    public void reset(boolean force) {
        if (poolState.compareAndSet(POOL_NORMAL, POOL_RESTING)) {
            commonLog.info("BeeOP({})begin to reset.", poolName);
            removeAllConnections(force, DESC_REMOVE_RESET);
            commonLog.info("All pooled objects were cleared");
            poolState.set(POOL_NORMAL);// restore state;
            commonLog.info("BeeOP({})finished resetting", poolName);
        }
    }

    public int getTotalSize() {
        return poolEntryArray.length;
    }

    public int getIdleSize() {
        int idleConnections = 0;
        for (PooledEntry pConn : this.poolEntryArray) {
            if (pConn.state == OBJECT_IDLE)
                idleConnections++;
        }
        return idleConnections;
    }

    public int getUsingSize() {
        int active = poolEntryArray.length - getIdleSize();
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

    public PoolMonitorVo getMonitorVo() {
        int totSize = getTotalSize();
        int idleSize = getIdleSize();
        monitorVo.setPoolName(poolName);
        monitorVo.setPoolMode(poolMode);
        monitorVo.setPoolState(poolState.get());
        monitorVo.setMaxActive(poolMaxSize);
        monitorVo.setIdleSize(idleSize);
        monitorVo.setUsingSize(totSize - idleSize);
        monitorVo.setSemaphoreWaiterSize(getSemaphoreWaitingSize());
        monitorVo.setTransferWaiterSize(getTransferWaitingSize());
        return monitorVo;
    }

    // register JMX
    private void registerJMX() {
        if (poolConfig.isEnableJMX()) {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            registerJMXBean(mBeanServer, String.format("cn.beeop.ObjectPool:type=beeop(%s)", poolName), this);
            registerJMXBean(mBeanServer, String.format("cn.beeop.PoolConfig:type=beeop(%s)-config", poolName), poolConfig);
        }
    }

    private void registerJMXBean(MBeanServer mBeanServer, String regName, Object bean) {
        try {
            ObjectName jmxRegName = new ObjectName(regName);
            if (!mBeanServer.isRegistered(jmxRegName)) {
                mBeanServer.registerMBean(bean, jmxRegName);
            }
        } catch (Exception e) {
            commonLog.warn("BeeOP({})failed to register jmx-bean:{}", poolName, regName, e);
        }
    }

    // unregister JMX
    private void unregisterJMX() {
        if (poolConfig.isEnableJMX()) {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            unregisterJMXBean(mBeanServer, String.format("cn.beeop.ObjectPool:type=beeop(%s)", poolName));
            unregisterJMXBean(mBeanServer, String.format("cn.beeop.PoolConfig:type=beeop(%s)-config", poolName));
        }
    }

    private void unregisterJMXBean(MBeanServer mBeanServer, String regName) {
        try {
            ObjectName jmxRegName = new ObjectName(regName);
            if (mBeanServer.isRegistered(jmxRegName)) {
                mBeanServer.unregisterMBean(jmxRegName);
            }
        } catch (Exception e) {
            commonLog.warn("BeeOP({})failed to unregister jmx-bean:{}", poolName, regName, e);
        }
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
            return OBJECT_IDLE;
        }

        public final boolean tryCatch(PooledEntry pConn) {
            return ObjStUpd.compareAndSet(pConn, OBJECT_IDLE, OBJECT_USING);
        }

        public final void onFailedTransfer(PooledEntry pConn) {
        }

        public final void beforeTransfer(PooledEntry pConn) {
            pConn.state = OBJECT_IDLE;
        }
    }

    static final class FairTransferPolicy implements TransferPolicy {
        public final int getCheckStateCode() {
            return OBJECT_USING;
        }

        public final boolean tryCatch(PooledEntry pConn) {
            return pConn.state == OBJECT_USING;
        }

        public final void onFailedTransfer(PooledEntry pConn) {
            pConn.state = OBJECT_IDLE;
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
                            if ((pConn = createPooledEntry(OBJECT_USING)) != null)
                                recycle(pConn);
                        } catch (ObjectException e) {
                            transferException(e);
                        }
                    }
                }

                if (needAddConnSize.get() == 0 && createObjThreadState.compareAndSet(THREAD_WORKING, THREAD_WAITING))
                    park(this);
                if (createObjThreadState.get() == THREAD_DEAD) break;
            }
        }
    }

    /**
     * Hook when JVM exit
     */
    private class PoolHook extends Thread {
        public void run() {
            try {
                ObjectPool.this.close();
            } catch (ObjectException e) {
                commonLog.error("Error on closing pool,cause:", e);
            }
        }
    }
}
