package com.example.shop.config;

import io.github.ilyaslabs.evl.EVL;

@EVL.Configuration(id = "config.payments", name = "Payments configuration", prefix = "shop.payments")
public class PaymentsConfig {

    @EVL.ConfigProperty(key = "shop.payments.timeout", defaultValue = "5s", description = "PSP call timeout")
    public String timeout;

    @EVL.ConfigProperty(key = "shop.payments.api-key", sensitive = true, required = true,
        description = "PSP API key, injected from the vault")
    public String apiKey;
}
