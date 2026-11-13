package com.example.shop.order;

import io.github.ilyaslabs.evl.EVL;
import java.util.List;

@EVL.Request(id = "order.create-request", name = "Create Order Request", validation = "At least one line")
public class CreateOrderRequest {
    public List<String> skus;
    public String customerId;
}
