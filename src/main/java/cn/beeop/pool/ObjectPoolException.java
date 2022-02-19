/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

/**
 * Bee object pool exception
 * <p>
 * Email:  Chris2018998@tom.com
 * Project: https://github.com/Chris2018998/beeop
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class ObjectPoolException extends Exception {

    public ObjectPoolException(String s) {
        super(s);
    }

    public ObjectPoolException(String message, Throwable cause) {
        super(message, cause);
    }

}

