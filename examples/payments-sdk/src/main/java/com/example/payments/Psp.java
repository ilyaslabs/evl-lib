package com.example.payments;

import io.github.ilyaslabs.evl.EVL;

@EVL.ExternalSystem(id = "payment.psp", name = "Payment Service Provider", systemType = EVL.SystemType.PAYMENT,
    url = "https://docs.example.com/psp")
public interface Psp {
    String authorize(String orderId, long amount);
}
