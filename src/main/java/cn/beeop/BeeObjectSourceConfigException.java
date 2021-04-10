/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU General Public License version 3.0.
 */
package cn.beeop;

/**
 * configuration exception
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class BeeObjectSourceConfigException extends RuntimeException {

    public BeeObjectSourceConfigException() {
        super();
    }

    public BeeObjectSourceConfigException(String s) {
        super(s);
    }

    public BeeObjectSourceConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public BeeObjectSourceConfigException(Throwable cause) {
        super(cause);
    }
}
