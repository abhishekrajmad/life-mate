package com.lifemate.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicExposureSecurityValidatorTest {

    @Test
    void permitsDisabledAuthenticationOnlyOnLoopback() {
        GatewaySecurityProperties security = new GatewaySecurityProperties();

        assertThatCode(() -> new PublicExposureSecurityValidator(security, "127.0.0.1").validate())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new PublicExposureSecurityValidator(security, "0.0.0.0").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be enabled");
    }

    @Test
    void enabledAuthenticationRequiresTwoStrongDistinctTokens() {
        GatewaySecurityProperties security = new GatewaySecurityProperties();
        security.setEnabled(true);
        security.setApiToken("api-token-with-at-least-32-characters");
        security.setWebhookToken("webhook-token-with-at-least-32-characters");

        assertThatCode(() -> new PublicExposureSecurityValidator(security, "0.0.0.0").validate())
                .doesNotThrowAnyException();

        security.setWebhookToken(security.getApiToken());
        assertThatThrownBy(() -> new PublicExposureSecurityValidator(security, "0.0.0.0").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be different");
    }

    @Test
    void rejectsMissingCredentialIdentity() {
        GatewaySecurityProperties security = new GatewaySecurityProperties();
        security.setApiUserId(" ");

        assertThatThrownBy(() -> new PublicExposureSecurityValidator(security, "127.0.0.1").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GATEWAY_API_USER_ID");
    }
}
