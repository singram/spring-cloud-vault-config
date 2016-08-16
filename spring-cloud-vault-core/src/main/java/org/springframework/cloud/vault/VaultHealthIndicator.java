/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.vault;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;
/**
 * @author Stuart Ingram
 */
@Component
public class VaultHealthIndicator extends AbstractHealthIndicator {

	@Autowired
	private VaultClient vaultClient;

	@Autowired
	public VaultHealthIndicator() {
	}

	//	Sealed or not initialized (Status 500): DOWN
	//	Standby (Status 429): OUT_OF_SERVICE
	//	Active (Status 200): UP
	//	All other codes: DOWN

	@Override
	protected void doHealthCheck(Health.Builder builder) {

		try {
			VaultHealthResponse vaultHealthResponse = vaultClient.health();
			if(!vaultHealthResponse.isInitialized() || vaultHealthResponse.isSealed()) {
				builder.down();
			} else if (vaultHealthResponse.isStandby()) {
				builder.outOfService();
			} else if (!vaultHealthResponse.isSealed()) {
				builder.up();
			} else {
				builder.down();
			}
		}
		catch(Exception e) {
			builder.down();
		}
	}
}