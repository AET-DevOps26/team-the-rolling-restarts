package rolling_restarts.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifies the JWK signing behaviour that lets user-service run with multiple replicas:
 * every replica that loads the same RSA key must advertise an identical JWK (same kid),
 * otherwise the gateway's kid-based key lookup fails intermittently behind the load balancer.
 */
class AuthorizationServerConfigTest {

	// Dev key pair (mirrors the one wired into the deployment manifests).
	private static final String PUBLIC_KEY = """
			-----BEGIN PUBLIC KEY-----
			MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw8TIIrmWZ2MAWnnK7G8P
			n39F1k2Wp6EKIsDIOkFQZ56xiNvGFP+JJsClVYCxkwqsfx/6ErGhqaJj1KhQBjxi
			TqVGJePEPXMMVBenfa7TOkpRoJh4sz95r3A1oruSKyb58euvhvlACERQcWJwe7cB
			c1SZsX6hM3iAC1UkA7Sd+zrQWuAKT2f/B13vRhu8j3PDA+1/yvoHBfef+EJWCbUp
			kO6YvuUg6IexTQ/jyRXd9PxxuTRcg4QIUe/YaW5uSThaGSEECj//nYeuCIuXHcYm
			CVBH5gzqtA+NBehaNUcxC5ImMgAz6mAVkbcWfDc1AAnffsaE9mlCrY+65vg1eml4
			4QIDAQAB
			-----END PUBLIC KEY-----
			""";

	private static final String PRIVATE_KEY = """
			-----BEGIN PRIVATE KEY-----
			MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDDxMgiuZZnYwBa
			ecrsbw+ff0XWTZanoQoiwMg6QVBnnrGI28YU/4kmwKVVgLGTCqx/H/oSsaGpomPU
			qFAGPGJOpUYl48Q9cwxUF6d9rtM6SlGgmHizP3mvcDWiu5IrJvnx66+G+UAIRFBx
			YnB7twFzVJmxfqEzeIALVSQDtJ37OtBa4ApPZ/8HXe9GG7yPc8MD7X/K+gcF95/4
			QlYJtSmQ7pi+5SDoh7FND+PJFd30/HG5NFyDhAhR79hpbm5JOFoZIQQKP/+dh64I
			i5cdxiYJUEfmDOq0D40F6Fo1RzELkiYyADPqYBWRtxZ8NzUACd9+xoT2aUKtj7rm
			+DV6aXjhAgMBAAECggEAHgHkQXpcBQXmUHf5tEsDxIlwLXygGpqzIIeXSWKojfGD
			ufwh/Sb8IV4HGbeLwIOy1BuVK9dhdcyH69lOxt3rna10tqsWcerYGW20xS2OgBFT
			OMAWRDd3Ef6rogGJUqb7Ses78P70oy/BetfBcR0okkKF+nB0Ch0u103aIt1FF/Rz
			sm5fgqLk/fU7dG0hiQs/mnT2sP3ugY0Q5vcUsGUu3dEZBNGxu7ivn/d//hJd8ucl
			ktxbxt4wN0d1MBa5/VDUbC4RFoDH9gZY0LpyAseEyQiCa/TNWhx82+LW4ZJNv4h0
			TuIZRjFQNv3Lu9YGZk1yXpuO+sxjbO3d6HBu+7zzFwKBgQDrjvKqcFfsM48MgQE8
			kc9jvXW2Pwar64whrLJSkd+plNotQhKgyujns+Gkw55RUT7zvhwr8HgBm0gkA58n
			c7eyY+Ow5pxYcpx29lrh28yy768qVY9t1/hd3w7pFcuHOI1qJm4aEwv9CSO3Y3e6
			CXCZCIWPiXN1TCQrhVxkDqaIDwKBgQDUweJ+iojdWlUtHZg9Pl1ElpaK8j2Nf5O1
			VwSf5wy0V3dqtmKjCzKFvkG60i8Vv+kOxKzPJ+ZypuoNoP+hdEXU9GOon6klTk7s
			76CTsIK+RtfhwRUirIQUCjnKToJYREo4ifYSIzR8OCXSkwOKzlZ9arlbGQ6rQxYx
			Uxl7W2CADwKBgQDcMkl3uAMIov3S03kjdK4ob3/s+Ce1aEAbboNXmlM5BBObgQtG
			0cc8aTPCQLbrDwbI9OsCzAxyfATI3bXWPF743FWJFVoLeD/5GLvGLCDDGDlnWon0
			RZI/tfcLPjt+0QeotfnwV+O6CuQfV1UhTBe8hnHLxtJuUNny8Px38BYOUwKBgFmi
			seT0QsNXWolebZRn4O2TsCsr8cjalgWRes5PjqewLgyUeKwOfKOiS5kFEndMWkkx
			jUjdm5Z0QimpyelgzkbxC8ewNJgWDOR/9JwkgoDd4fgn29q26hJzWHOmbf34D3kQ
			js6HbIZlNXIpJBXn5sKCI0OBJA/9fK0dQKAbGGX7AoGAet3lt7d9OZgP2PUpZi23
			x1DwMtdf0fPz5wi97B3oPpOMnNPC69wjvVl8IvJVn+FZJ1wfTzcglpUh5aEL9C8j
			qOIio09BWfoa794j5gqNAa4SP71cmVpsJ9TAe6tP0SLMnaoXUacu7Lv1RMa72xxp
			+MtKDLHcW7CT4ZMd9ho7OD0=
			-----END PRIVATE KEY-----
			""";

	private static String keyId(String publicKey, String privateKey) {
		AuthorizationServerConfig config = new AuthorizationServerConfig();
		ReflectionTestUtils.setField(config, "rsaPublicKeyPem", publicKey);
		ReflectionTestUtils.setField(config, "rsaPrivateKeyPem", privateKey);
		JWKSource<SecurityContext> source = config.jwkSource();
		JWKSet jwkSet = ((ImmutableJWKSet<SecurityContext>) source).getJWKSet();
		assertThat(jwkSet.getKeys()).hasSize(1);
		return jwkSet.getKeys().get(0).getKeyID();
	}

	@Test
	void configuredKey_producesSameKidAcrossInstances() {
		// Two "replicas" loading the identical key must advertise the identical kid.
		String kidA = keyId(PUBLIC_KEY, PRIVATE_KEY);
		String kidB = keyId(PUBLIC_KEY, PRIVATE_KEY);

		assertThat(kidA).isEqualTo(kidB);
		// A SHA-256 thumbprint is 43 base64url chars and contains no '-' (unlike a UUID kid).
		assertThat(kidA).hasSize(43).doesNotContain("-");
	}

	@Test
	void generatedFallbackKey_isUsedWhenNoKeyConfigured() {
		// With no key configured the service still starts (ephemeral key, dev-only path).
		String kid = keyId("", "");
		assertThat(kid).isNotBlank().hasSize(43);
	}
}
