/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU General Public License version 3.0.
 */
package cn.beeop;

import java.util.Properties;

/**
 * Object factory by class
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class BeeClassObjectFactory implements BeeObjectFactory {
    private Class objectClass;

    public BeeClassObjectFactory(Class objectClass) {
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

    //destroy  object
    public void destroy(Object obj) {

    }

    //test object
    public boolean isAlive(Object obj, int timeout) {
        return true;
    }
}
