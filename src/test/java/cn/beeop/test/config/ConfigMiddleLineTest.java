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
package cn.beeop.test.config;

import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.BeeObjectSourceConfigException;
import cn.beeop.test.TestCase;

import java.net.URL;

/**
 * @author Chris.Liao
 * @version 1.0
 */
public class ConfigMiddleLineTest extends TestCase {
    public void test() throws Exception {
        String filename= "ConfigMiddleLineTest.properties";
        URL url = ConfigMiddleLineTest.class.getResource(filename);
        url = ConfigMiddleLineTest.class.getClassLoader().getResource(filename);

        BeeObjectSourceConfig testConfig = new BeeObjectSourceConfig();
        testConfig.loadFromPropertiesFile(url.getFile());

        if(!"BeeOP".equals(testConfig.getPoolName()))throw new BeeObjectSourceConfigException("poolName error");
        if(!testConfig.isFairMode())throw new BeeObjectSourceConfigException("fairMode error");
        if(testConfig.getInitialSize()!=5)throw new BeeObjectSourceConfigException("initialSize error");
        if(testConfig.getMaxActive()!=10)throw new BeeObjectSourceConfigException("maxActive error");
    }
}