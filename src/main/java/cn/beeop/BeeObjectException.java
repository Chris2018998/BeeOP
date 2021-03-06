/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU General Public License version 3.0.
 */
package cn.beeop;

/**
 * Bee object exception
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class BeeObjectException extends Exception {

    public BeeObjectException() {
        super();
    }

    public BeeObjectException(String s) {
        super(s);
    }

    public BeeObjectException(Throwable cause) {
        super(cause);
    }

    public BeeObjectException(String message, Throwable cause) {
        super(message, cause);
    }

}
