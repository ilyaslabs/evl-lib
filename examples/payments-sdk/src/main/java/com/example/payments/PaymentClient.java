package com.example.payments;

import io.github.ilyaslabs.evl.EVL;

@EVL.Component(id = "payment.client", name = "Payment Client", layer = EVL.Layer.INTEGRATION)
@EVL.Dependency(target = "payment.psp", type = EVL.DependencyType.EXTERNAL, reason = "Card authorization")
public class PaymentClient {

    @EVL.Flow(id = "payment.authorize", name = "Authorize Payment", category = EVL.Category.INTEGRATION)
    public String authorize(String orderId, long amount) {
        return "auth-" + orderId;
    }

    @EVL.Flow(id = "payment.capture", name = "Capture Payment", category = EVL.Category.INTEGRATION)
    public void capture(String authorization) {
        // captures funds
    }
}
