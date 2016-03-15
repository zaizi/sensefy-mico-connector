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

import org.zaizi.mico.client.MicoClientFactory;

/**
 * Parameters for MICO transformation connector.
 */
public class MicoConfig {
	
	// Specification nodes and values
	public static final String NODE_MICO_SERVER = "micoServer";
	public static final String NODE_MICO_USER = "micoUser";
	public static final String NODE_MICO_PASSWORD = "micoPassword";
	public static final String NODE_MICO_DOC_URI_FIELD="micoDocUriField";
	public static final String ATTRIBUTE_VALUE = "value";

	private static MicoClientFactory micoClientFactory;

	public static synchronized MicoClientFactory getMicoClientFactory(String micoServer, String micoUser, String micoPassword)
			{
		if (micoClientFactory == null) {
			micoClientFactory = new MicoClientFactory(micoServer, micoUser, micoPassword);
		}
		return micoClientFactory;
	}
}
