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
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

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
	public AuthorizationServerSettings authorizationServerSettings() {
		return AuthorizationServerSettings.builder().build();
	}
}
