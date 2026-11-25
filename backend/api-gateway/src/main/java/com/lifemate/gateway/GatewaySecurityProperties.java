package com.lifemate.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    private boolean enabled;
    private String apiToken = "";
    private String webhookToken = "";
    private String apiUserId = "usr_abhishek";
    private String webhookUserId = "usr_abhishek";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getWebhookToken() {
        return webhookToken;
    }

    public void setWebhookToken(String webhookToken) {
        this.webhookToken = webhookToken;
    }

    public String getApiUserId() {
        return apiUserId;
    }

    public void setApiUserId(String apiUserId) {
        this.apiUserId = apiUserId;
    }

    public String getWebhookUserId() {
        return webhookUserId;
    }

    public void setWebhookUserId(String webhookUserId) {
        this.webhookUserId = webhookUserId;
    }
}
