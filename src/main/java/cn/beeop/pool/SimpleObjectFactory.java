/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectFactory;

import java.util.Properties;

/**
 * Object instance factory by class
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class SimpleObjectFactory implements BeeObjectFactory {
    private Class objectClass;

    public SimpleObjectFactory(Class objectClass) {
        this.objectClass = objectClass;
    }

    //create object instance
    public Object create(Properties prop) throws BeeObjectException {
        try {
            return objectClass.newInstance();
        } catch (Throwable e) {
            throw new BeeObjectException(e);
        }
    }

    //set default values
    public void setDefault(Object obj) throws BeeObjectException {
    }

    //set default values
    public void reset(Object obj) throws BeeObjectException {
    }

    //test object
    public boolean isValid(Object obj, int timeout) {
        return true;
    }

    //destroy  object
    public void destroy(Object obj) {
    }
}
