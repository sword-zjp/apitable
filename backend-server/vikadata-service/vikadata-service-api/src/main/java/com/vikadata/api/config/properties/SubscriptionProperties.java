package com.vikadata.api.config.properties;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "vikadata.subscription")
public class SubscriptionProperties {

    private Boolean testMode = false;
}
