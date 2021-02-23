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

import java.util.Properties;

/**
 * Bee raw object factory
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

    //test raw object
    public boolean isAlive(Object obj, long timeout);

    //destroy raw object on removed from pool
    public void destroy(Object obj);
}
