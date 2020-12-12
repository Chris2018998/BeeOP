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
    private static final String DESC_REMOVE_PREINIT = "pre-init";
    private static final String DESC_REMOVE_INIT = "init";
    private static final String DESC_REMOVE_BAD = "bad";
    private static final String DESC_REMOVE_IDLE = "idle";
    private static final String DESC_REMOVE_CLOSED = "closed";
    private static final String DESC_REMOVE_RESET = "reset";
    private static final String DESC_REMOVE_DESTROY = "destroy";
    private static final AtomicInteger poolNameIndex = new AtomicInteger(1);

    private final Object entryArrayLock = new Object();
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
    private int borrowSemaphoreSize;
    private Semaphore borrowSemaphore;
    private TransferPolicy transferPolicy;
    private ConcurrentLinkedQueue<Borrower> waitQueue = new ConcurrentLinkedQueue<Borrower>();

    private ObjectFactory objectFactory;
    private PooledConnAddThread poolEntryAddThread;
    private volatile PooledEntry[] poolEntryArray = new PooledEntry[0];
    private ScheduledFuture<?> idleCheckSchFuture;
    private ScheduledThreadPoolExecutor idleSchExecutor = new ScheduledThreadPoolExecutor(2, new PoolThreadThreadFactory("IdleObjectScan"));
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
            borrowSemaphoreSize = poolConfig.getBorrowSemaphoreSize();
            borrowSemaphore = new Semaphore(borrowSemaphoreSize, poolConfig.isFairMode());
            idleSchExecutor.setKeepAliveTime(15, SECONDS);
            idleSchExecutor.allowCoreThreadTimeOut(true);
            idleCheckSchFuture = idleSchExecutor.scheduleAtFixedRate(new Runnable() {
                                                                         public void run() {// check idle object
                                                                             closeIdleTimeoutObject();
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
                    borrowSemaphoreSize,
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

    private final boolean existBorrower() {
        return borrowSemaphoreSize > borrowSemaphore.availablePermits();
    }

    //create Pooled object
    private PooledEntry createPooledEntry(int connState) throws ObjectException {
        synchronized (entryArrayLock) {
            int arrayLen = poolEntryArray.length;
            if (arrayLen < poolMaxSize) {
                commonLog.debug("BeeOP({}))begin to create new pooled object,state:{}", poolName, connState);
                Object obj = objectFactory.create(createProperties);
                objectFactory.setDefault(obj);
                PooledEntry pEntry = new PooledEntry(obj, connState, this, poolConfig);// add
                commonLog.debug("BeeOP({}))has created new pooled object:{},state:{}", poolName, pEntry, connState);
                PooledEntry[] arrayNew = new PooledEntry[arrayLen + 1];
                arraycopy(poolEntryArray, 0, arrayNew, 0, arrayLen);
                arrayNew[arrayLen] = pEntry;// tail
                poolEntryArray = arrayNew;
                return pEntry;
            } else {
                return null;
            }
        }
    }

    //remove Pooled object
    private void removePooledEntry(PooledEntry pEntry, String removeType) {
        commonLog.debug("BeeOP({}))begin to remove pooled object:{},reason:{}", poolName, pEntry, removeType);
        pEntry.state = OBJECT_CLOSED;
        // pEntry.closeRawConn();
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
        if (initSize == 0) {//try to create one
            Object object = null;
            try {
                object = objectFactory.create(createProperties);
                objectFactory.setDefault(object);
            } catch (Throwable e) {
            } finally {
                if (object != null) try {
                    objectFactory.destroy(object);
                } catch (Throwable e) {
                }
            }
        } else {
            try {
                for (int i = 0; i < initSize; i++)
                    createPooledEntry(OBJECT_IDLE);
            } catch (ObjectException e) {
                for (PooledEntry pEntry : poolEntryArray)
                    removePooledEntry(pEntry, DESC_REMOVE_INIT);
                throw e;
            }
        }
    }

    /**
     * borrow one object from pool
     *
     * @return If exists idle object in pool,then return one;if not, waiting
     * until other borrower release
     * @throws ObjectException if pool is closed or waiting timeout,then throw exception
     */
    public ProxyObject getObject() throws ObjectException {
        if (poolState.get() != POOL_NORMAL) throw PoolCloseException;

        //0:try to get from threadLocal cache
        WeakReference<Borrower> ref = threadLocal.get();
        Borrower borrower = (ref != null) ? ref.get() : null;
        if (borrower != null) {
            PooledEntry pEntry = borrower.lastUsedEntry;
            if (pEntry != null && pEntry.state == OBJECT_IDLE && ObjStUpd.compareAndSet(pEntry, OBJECT_IDLE, OBJECT_USING)) {
                if (testOnBorrow(pEntry)) {
                    borrower.lastUsedEntry = pEntry;
                    return new ProxyObject(pEntry);
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
            PooledEntry pEntry;
            PooledEntry[] tempArray = poolEntryArray;
            for (int i = 0, l = tempArray.length; i < l; i++) {
                pEntry = tempArray[i];
                if (pEntry.state == OBJECT_IDLE && ObjStUpd.compareAndSet(pEntry, OBJECT_IDLE, OBJECT_USING) && testOnBorrow(pEntry)) {
                    borrower.lastUsedEntry = pEntry;
                    return new ProxyObject(pEntry);
                }
            }

            //2:try to create one directly
            if (poolEntryArray.length < poolMaxSize && (pEntry = createPooledEntry(OBJECT_USING)) != null) {
                borrower.lastUsedEntry = pEntry;
                return new ProxyObject(pEntry);
            }

            //3:try to get one transferred object
            boolean failed = false;
            ObjectException failedCause = null;
            Thread cThread = borrower.thread;
            borrower.state = BORROWER_NORMAL;
            waitQueue.offer(borrower);
            int spinSize = (waitQueue.peek() == borrower) ? maxTimedSpins : 0;

            while (true) {
                Object state = borrower.state;
                if (state instanceof PooledEntry) {
                    pEntry = (PooledEntry) state;
                    if (transferPolicy.tryCatch(pEntry) && testOnBorrow(pEntry)) {
                        waitQueue.remove(borrower);
                        borrower.lastUsedEntry = pEntry;
                        return new ProxyObject(pEntry);
                    }

                    state = BORROWER_NORMAL;
                    borrower.state = state;
                    yield();
                } else if (state instanceof ObjectException) {
                    waitQueue.remove(borrower);
                    throw (ObjectException) state;
                }

                if (failed) {
                    if(borrower.state==state)
                         BwrStUpd.compareAndSet(borrower, state, failedCause);
                } else {
                    long timeout = deadline - nanoTime();
                    if (timeout > 0L) {
                        if (spinSize > 0) {
                            spinSize--;
                        } else if (timeout - spinForTimeoutThreshold > 0 && borrower.state==state && BwrStUpd.compareAndSet(borrower, state, BORROWER_WAITING)) {
                            parkNanos(borrower, timeout);
                            if (cThread.isInterrupted()) {
                                failed = true;
                                failedCause = RequestInterruptException;
                            }
                            if (borrower.state == BORROWER_WAITING)
                                BwrStUpd.compareAndSet(borrower, BORROWER_WAITING, failed ? failedCause : BORROWER_NORMAL);//reset to norma
                        }
                    } else {//timeout
                        failed = true;
                        failedCause = RequestTimeoutException;
                        if(borrower.state==state)
                          BwrStUpd.compareAndSet(borrower, state, failedCause);
                    }
                }
            }//while
        } finally {
            borrowSemaphore.release();
        }
    }

    /**
     * remove object
     *
     * @param pEntry target object need release
     */
    void abandonOnReturn(PooledEntry pEntry) {
        removePooledEntry(pEntry, DESC_REMOVE_BAD);
        tryToCreateNewConnByAsyn();
    }

    /**
     * return object to pool
     *
     * @param pEntry target object need release
     */
    final void recycle(PooledEntry pEntry) {
        transferPolicy.beforeTransfer(pEntry);

        for (Borrower borrower : waitQueue)
            for (Object state = borrower.state; state == BORROWER_NORMAL || state == BORROWER_WAITING; state = borrower.state) {
                if (pEntry.state - this.entryUnCatchStateCode != 0) return;
                if (BwrStUpd.compareAndSet(borrower, state, pEntry)) {
                    if (state == BORROWER_WAITING)
                        unpark(borrower.thread);
                    return;
                }
            }
        transferPolicy.onFailedTransfer(pEntry);
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
    private void closeIdleTimeoutObject() {
        if (poolState.get() == POOL_NORMAL) {
            PooledEntry[] array = poolEntryArray;
            for (int i = 0, len = array.length; i < len; i++) {
                PooledEntry pEntry = array[i];
                int state = pEntry.state;
                if (state == OBJECT_IDLE && !existBorrower()) {
                    boolean isTimeoutInIdle = (currentTimeMillis() - pEntry.lastAccessTime - poolConfig.getIdleTimeout() >= 0);
                    if (isTimeoutInIdle && ObjStUpd.compareAndSet(pEntry, state, OBJECT_CLOSED)) {//need close idle
                        removePooledEntry(pEntry, DESC_REMOVE_IDLE);
                        tryToCreateNewConnByAsyn();
                    }
                } else if (state == OBJECT_USING) {
                    ProxyObject proxyObject = pEntry.proxyObject;
                    boolean isHoldTimeoutInNotUsing = currentTimeMillis() - pEntry.lastAccessTime - poolConfig.getHoldTimeout() >= 0;
                    if (isHoldTimeoutInNotUsing && proxyObject != null) {//recycle object
                        proxyObject.trySetAsClosed();
                    }
                } else if (state == OBJECT_CLOSED) {
                    removePooledEntry(pEntry, DESC_REMOVE_CLOSED);
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
                removeAllObjects(poolConfig.isForceClose(), DESC_REMOVE_DESTROY);
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
    private void removeAllObjects(boolean force, String source) {
        while (existBorrower()) {
            transferException(PoolCloseException);
        }

        long parkNanoSeconds = SECONDS.toNanos(poolConfig.getWaitTimeToClearPool());
        while (poolEntryArray.length > 0) {
            PooledEntry[] array = poolEntryArray;
            for (int i = 0, len = array.length; i < len; i++) {
                PooledEntry pEntry = array[i];
                if (ObjStUpd.compareAndSet(pEntry, OBJECT_IDLE, OBJECT_CLOSED)) {
                    removePooledEntry(pEntry, source);
                } else if (pEntry.state == OBJECT_CLOSED) {
                    removePooledEntry(pEntry, source);
                } else if (pEntry.state == OBJECT_USING) {
                    ProxyObject proxyObject = pEntry.proxyObject;
                    if (force) {
                        if (proxyObject != null) {
                            proxyObject.trySetAsClosed();
                        }
                    } else {
                        boolean isTimeout = (currentTimeMillis() - pEntry.lastAccessTime - poolConfig.getHoldTimeout() >= 0);
                        if (isTimeout && proxyObject != null) {
                            proxyObject.trySetAsClosed();
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
        while (true) {
            int curAddSize = needAddConnSize.get();
            int updAddSize = curAddSize + 1;
            if (poolEntryArray.length + updAddSize > poolMaxSize) return;
            if (needAddConnSize.compareAndSet(curAddSize, updAddSize)) {
                if (createObjThreadState.get() == THREAD_WAITING && createObjThreadState.compareAndSet(THREAD_WAITING, THREAD_WORKING))
                    unpark(poolEntryAddThread);
                return;
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
            removeAllObjects(force, DESC_REMOVE_RESET);
            commonLog.info("All pooled objects were cleared");
            poolState.set(POOL_NORMAL);// restore state;
            commonLog.info("BeeOP({})finished resetting", poolName);
        }
    }

    public int getTotalSize() {
        return poolEntryArray.length;
    }

    public int getIdleSize() {
        int idleObjects = 0;
        for (PooledEntry pEntry : this.poolEntryArray) {
            if (pEntry.state == OBJECT_IDLE)
                idleObjects++;
        }
        return idleObjects;
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

        void beforeTransfer(PooledEntry pEntry);

        boolean tryCatch(PooledEntry pEntry);

        void onFailedTransfer(PooledEntry pEntry);
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

        public final boolean tryCatch(PooledEntry pEntry) {
            return ObjStUpd.compareAndSet(pEntry, OBJECT_IDLE, OBJECT_USING);
        }

        public final void onFailedTransfer(PooledEntry pEntry) {
        }

        public final void beforeTransfer(PooledEntry pEntry) {
            pEntry.state = OBJECT_IDLE;
        }
    }

    static final class FairTransferPolicy implements TransferPolicy {
        public final int getCheckStateCode() {
            return OBJECT_USING;
        }

        public final boolean tryCatch(PooledEntry pEntry) {
            return pEntry.state == OBJECT_USING;
        }

        public final void onFailedTransfer(PooledEntry pEntry) {
            pEntry.state = OBJECT_IDLE;
        }

        public final void beforeTransfer(PooledEntry pEntry) {
        }
    }

    // create object to pool
    private class PooledConnAddThread extends Thread {
        public void run() {
            PooledEntry pEntry;
            while (true) {
                while (needAddConnSize.get() > 0) {
                    needAddConnSize.decrementAndGet();
                    if (getTransferWaitingSize() > 0) {
                        try {
                            if ((pEntry = createPooledEntry(OBJECT_USING)) != null)
                                recycle(pEntry);
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
