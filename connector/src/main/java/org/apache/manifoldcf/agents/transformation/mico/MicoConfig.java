/* $Id$ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.manifoldcf.agents.transformation.mico;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

import eu.mico.platform.event.api.EventManager;
import eu.mico.platform.event.impl.EventManagerImpl;

/**
 * Parameters for Tika transformation connector.
 */
public class MicoConfig {
	
	// Specification nodes and values
	public static final String NODE_MICO_SERVER = "micoserver";
	public static final String NODE_MICO_USER = "micouser";
	public static final String NODE_MICO_PASSWORD = "micopassword";
	public static final String ATTRIBUTE_VALUE = "value";

	private static EventManager eventManager;

	public static synchronized EventManager EventManager(String micoServer, String micoUser, String micoPassword)
			throws IOException, TimeoutException, URISyntaxException {
		if (eventManager == null) {
			eventManager = new EventManagerImpl(micoServer, micoUser, micoPassword);
			eventManager.init();
		}
		return eventManager;
	}
}
