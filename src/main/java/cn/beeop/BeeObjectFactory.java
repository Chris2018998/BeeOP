/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU General Public License version 3.0.
 */
package cn.beeop;

import java.util.Properties;

/**
 * Object instance factory
 *
 * @author Chris
 * @version 1.0
 */
public interface BeeObjectFactory {

    //create object instance
    public Object create(Properties prop) throws BeeObjectException;

    //set default values to raw object on initialization
    public void setDefault(Object obj) throws BeeObjectException;

    //reset some changed properties in raw object on returning
    public void reset(Object obj) throws BeeObjectException;

    //test raw object valid
    public boolean isValid(Object obj, int timeout);

    //destroy raw object on removed from pool
    public void destroy(Object obj);
}
