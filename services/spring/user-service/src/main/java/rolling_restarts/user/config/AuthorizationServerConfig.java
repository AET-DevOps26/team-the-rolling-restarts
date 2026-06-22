package rolling_restarts.user.config;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

@Configuration
public class AuthorizationServerConfig {

	private static final Logger log = LoggerFactory.getLogger(AuthorizationServerConfig.class);

	@Value("${jwt.rsa.public-key:}")
	private String rsaPublicKeyPem;

	@Value("${jwt.rsa.private-key:}")
	private String rsaPrivateKeyPem;

	@Bean
	public JWKSource<SecurityContext> jwkSource() {
		KeyPair keyPair;
		if (rsaPublicKeyPem != null && !rsaPublicKeyPem.isBlank()
				&& rsaPrivateKeyPem != null && !rsaPrivateKeyPem.isBlank()) {
			log.info("Loading RSA key pair from configuration");
			keyPair = loadKeyPair(rsaPublicKeyPem, rsaPrivateKeyPem);
		} else {
			log.warn("No RSA keys configured — generating ephemeral key pair (not suitable for production)");
			keyPair = generateRsaKey();
		}

		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
		RSAKey rsaKey = new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(UUID.randomUUID().toString())
				.build();
		JWKSet jwkSet = new JWKSet(rsaKey);
		return new ImmutableJWKSet<>(jwkSet);
	}

	private static KeyPair loadKeyPair(String publicKeyPem, String privateKeyPem) {
		try {
			String publicKeyBase64 = publicKeyPem
					.replace("-----BEGIN PUBLIC KEY-----", "")
					.replace("-----END PUBLIC KEY-----", "")
					.replaceAll("\\s", "");
			String privateKeyBase64 = privateKeyPem
					.replace("-----BEGIN PRIVATE KEY-----", "")
					.replace("-----END PRIVATE KEY-----", "")
					.replaceAll("\\s", "");

			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
					new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
			RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
					new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64)));

			return new KeyPair(publicKey, privateKey);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to load RSA key pair from PEM configuration", ex);
		}
	}

	private static KeyPair generateRsaKey() {
		try {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(2048);
			return keyPairGenerator.generateKeyPair();
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Bean
	public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
		return new NimbusJwtEncoder(jwkSource);
	}

	@Bean
	public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
	}

	/**
	 * The Authorization Server requires at least one registered client to initialize its
	 * protocol endpoints (JWKS, OIDC discovery). Registers the web-client SPA as a public
	 * client using authorization_code + PKCE (no secret).
	 */
	@Bean
	public RegisteredClientRepository registeredClientRepository(
			@Value("${jwt.web-client-redirect-uri:http://127.0.0.1:3000/login/oauth2/code/web-client}") String redirectUri) {
		RegisteredClient webClient = RegisteredClient.withId(UUID.randomUUID().toString())
				.clientId("web-client")
				.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.redirectUri(redirectUri)
				.scope(OidcScopes.OPENID)
				.scope(OidcScopes.PROFILE)
				.clientSettings(ClientSettings.builder().requireProofKey(true).build())
				.build();
		return new InMemoryRegisteredClientRepository(webClient);
	}

	@Bean
	public AuthorizationServerSettings authorizationServerSettings(@Value("${jwt.issuer}") String issuer) {
		return AuthorizationServerSettings.builder().issuer(issuer).build();
	}
}
