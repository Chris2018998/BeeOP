/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU General Public License version 3.0.
 */
package cn.beeop.pool;

/**
 * Pooled object borrower
 *
 * @author Chris.Liao
 * @version 1.0
 */
final class Borrower {
    volatile Object state;
    PooledEntry lastUsedEntry;
    Thread thread = Thread.currentThread();
}