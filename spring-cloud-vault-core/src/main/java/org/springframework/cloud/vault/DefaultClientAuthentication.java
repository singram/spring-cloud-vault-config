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

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.cloud.vault.VaultProperties.AuthenticationMethod;
import org.springframework.cloud.vault.VaultProperties.Ssl;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import lombok.Value;
import lombok.extern.apachecommons.CommonsLog;

/**
 * Default implementation of {@link ClientAuthentication}.
 *
 * @author Mark Paluch
 */
@CommonsLog
class DefaultClientAuthentication extends ClientAuthentication {

	private final VaultProperties properties;
	private final VaultClient vaultClient;
	private final AppIdUserIdMechanism appIdUserIdMechanism;
	private char[] nonce;

	/**
	 * Creates a {@link DefaultClientAuthentication} using {@link VaultProperties} and
	 * {@link RestTemplate}.
	 *
	 * @param properties must not be {@literal null}.
	 * @param vaultClient must not be {@literal null}.
	 */
	DefaultClientAuthentication(VaultProperties properties, VaultClient vaultClient) {

		Assert.notNull(properties, "VaultProperties must not be null");
		Assert.notNull(vaultClient, "RestTemplate must not be null");

		this.properties = properties;
		this.vaultClient = vaultClient;
		this.appIdUserIdMechanism = null;
	}

	/**
	 * Creates a {@link DefaultClientAuthentication} using {@link VaultProperties} and
	 * {@link RestTemplate} for AppId authentication.
	 *
	 * @param properties must not be {@literal null}.
	 * @param vaultClient must not be {@literal null}.
	 * @param appIdUserIdMechanism must not be {@literal null}.
	 */
	DefaultClientAuthentication(VaultProperties properties, VaultClient vaultClient,
			AppIdUserIdMechanism appIdUserIdMechanism) {

		Assert.notNull(properties, "VaultProperties must not be null");
		Assert.notNull(vaultClient, "VaultClient must not be null");
		Assert.notNull(appIdUserIdMechanism, "AppIdUserIdMechanism must not be null");

		this.properties = properties;
		this.vaultClient = vaultClient;
		this.appIdUserIdMechanism = appIdUserIdMechanism;
	}

	@Override
	public VaultToken login() {

		if (properties.getAuthentication() == VaultProperties.AuthenticationMethod.APPID
				&& appIdUserIdMechanism != null) {
			log.info("Using AppId authentication to log into Vault");

			VaultProperties.AppIdProperties appId = properties.getAppId();
			return createTokenUsingAppId(new AppIdTuple(properties.getApplicationName(),
					appIdUserIdMechanism.createUserId()), appId);
		}

		if (properties.getAuthentication() == AuthenticationMethod.CERT
				&& properties.getSsl() != null) {
			log.info("Using TLS Certificate authentication to log into Vault");

			return createTokenUsingTlsCertAuthentication(properties.getSsl());
		}

		if (properties.getAuthentication() == VaultProperties.AuthenticationMethod.AWS_EC2) {
			log.info("Using AWS-EC2 authentication to log into Vault");

			return createTokenUsingAwsEc2();
		}

		throw new UnsupportedOperationException(String.format(
				"Cannot create a token for auth method %s",
				properties.getAuthentication()));
	}

	private VaultToken createTokenUsingAppId(AppIdTuple appIdTuple,
			VaultProperties.AppIdProperties appId) {

		URI uri = VaultClient.buildUri(properties,
				String.format("auth/%s/login", appId.getAppIdPath()));

		Map<String, String> login = getAppIdLogin(appIdTuple);

		VaultClientResponse response = vaultClient.write(uri, login);

		if (!response.isSuccessful()) {
			throw new IllegalStateException(String.format(
					"Cannot login using app-id: %s", response.getMessage()));
		}

		VaultResponse body = response.getBody();
		String token = (String) body.getAuth().get("client_token");

		log.debug("Login successful using AppId authentication");

		return VaultToken.of(token, body.getLeaseDuration());
	}

	private VaultToken createTokenUsingTlsCertAuthentication(Ssl ssl) {

		URI uri = VaultClient.buildUri(properties,
				String.format("auth/%s/login", ssl.getCertAuthPath()));

		VaultClientResponse response = vaultClient.write(uri, Collections.emptyMap());

		if (!response.isSuccessful()) {
			throw new IllegalStateException(String.format(
					"Cannot login using TLS certificates: %s", response.getMessage()));
		}

		VaultResponse body = response.getBody();
		String token = (String) body.getAuth().get("client_token");

		log.debug("Login successful using TLS certificates");

		return VaultToken.of(token, body.getLeaseDuration());
	}

	private Map<String, String> getAppIdLogin(AppIdTuple appIdTuple) {

		Map<String, String> login = new HashMap<>();
		login.put("app_id", appIdTuple.getAppId());
		login.put("user_id", appIdTuple.getUserId());
		return login;
	}

	@SuppressWarnings("unchecked")
	private VaultToken createTokenUsingAwsEc2() {

		VaultProperties.AwsEc2Properties awsEc2 = this.properties.getAwsEc2();

		URI uri = VaultClient.buildUri(this.properties,
				String.format("auth/%s/login", awsEc2.getAwsEc2Path()));

		Map<String, String> login = getEc2Login(awsEc2);

		VaultClientResponse response = vaultClient.write(uri, login);

		if (!response.isSuccessful()) {
			throw new IllegalStateException(String.format(
					"Cannot login using AWS-EC2: %s", response.getMessage()));
		}

		VaultResponse body = response.getBody();
		String token = (String) body.getAuth().get("client_token");

		if (log.isDebugEnabled()) {

			if (body.getAuth().get("metadata") instanceof Map) {
				Map<Object, Object> metadata = (Map<Object, Object>) body.getAuth().get(
						"metadata");
				log.debug(String
						.format("Login successful using AWS-EC2 authentication for instance %s, AMI %s",
								metadata.get("instance_id"), metadata.get("instance_id")));
			}
			else {
				log.debug("Login successful using AWS-EC2 authentication");
			}
		}

		return VaultToken.of(token, body.getLeaseDuration());
	}

	private Map<String, String> getEc2Login(VaultProperties.AwsEc2Properties properties) {

		Map<String, String> login = new HashMap<>();

		if (StringUtils.hasText(properties.getRole())) {
			login.put("role", properties.getRole());
		}

		if (properties.isUseNonce()) {
			if (this.nonce == null) {
				this.nonce = createNonce();
			}

			login.put("nonce", new String(this.nonce));
		}

		String pkcs7 = vaultClient.getRestTemplate().getForObject(
				properties.getIdentityDocument(), String.class);
		if (StringUtils.hasText(pkcs7)) {
			login.put("pkcs7", pkcs7.replaceAll("\\r", "").replace("\\n", ""));
		}
		return login;
	}

	private char[] createNonce() {
		return UUID.randomUUID().toString().toCharArray();
	}

	@Value
	private static class AppIdTuple {
		private String appId;
		private String userId;
	}
}
