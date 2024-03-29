/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.RawObjectFactory;

import java.lang.reflect.Constructor;

/**
 * Object instance factory by class
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class SimpleObjectFactory implements RawObjectFactory {
    private final Constructor constructor;

    public SimpleObjectFactory(Constructor constructor) {
        this.constructor = constructor;
    }

    //create object instance
    public Object create() throws Exception {
        return this.constructor.newInstance();
    }

    //set default values
    public void setDefault(Object obj) {
        //do nothing
    }

    //set default values
    public void reset(Object obj) {
        //do nothing
    }

    //test object
    public boolean isValid(Object obj, int timeout) {
        return true;
    }

    //destroy  object
    public void destroy(Object obj) {
        //do nothing
    }
}
