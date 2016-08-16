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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.vault.ClientHttpRequestFactoryFactory.HttpComponents;
import org.springframework.cloud.vault.ClientHttpRequestFactoryFactory.Netty;
import org.springframework.cloud.vault.ClientHttpRequestFactoryFactory.OkHttp;
import org.springframework.cloud.vault.util.Settings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.http.client.OkHttpClientHttpRequestFactory;

/**
 * Integration tests for {@link ClientHttpRequestFactory}.
 *
 * @author Mark Paluch
 * @auther Stuart Ingram
 */
public class ClientHttpRequestFactoryFactoryIntegrationTests {

	private VaultProperties vaultProperties = Settings.createVaultProperties();

	@Test
	public void httpComponentsClientShouldWork() throws Exception {

		ClientHttpRequestFactory factory = HttpComponents.usingHttpComponents(vaultProperties);

		VaultHealthResponse vaultHealthResponse = new VaultClient(vaultProperties, factory).health();

		assertThat(factory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
		assertThat(vaultHealthResponse.isInitialized()).isTrue();

		((DisposableBean) factory).destroy();
	}

	@Test
	public void nettyClientShouldWork() throws Exception {

		ClientHttpRequestFactory factory = Netty.usingNetty(vaultProperties);
		((InitializingBean) factory).afterPropertiesSet();

		VaultHealthResponse vaultHealthResponse = new VaultClient(vaultProperties, factory).health();

		assertThat(factory).isInstanceOf(Netty4ClientHttpRequestFactory.class);
		assertThat(vaultHealthResponse.isInitialized()).isTrue();

		((DisposableBean) factory).destroy();
	}

	@Test
	public void okHttpClientShouldWork() throws Exception {

		ClientHttpRequestFactory factory = OkHttp.usingOkHttp(vaultProperties);
		VaultHealthResponse vaultHealthResponse = new VaultClient(vaultProperties, factory).health();

		assertThat(factory).isInstanceOf(OkHttpClientHttpRequestFactory.class);
		assertThat(vaultHealthResponse.isInitialized()).isTrue();

		((DisposableBean) factory).destroy();
	}
}
