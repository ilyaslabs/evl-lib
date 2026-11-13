package com.example.shop.order;

import io.github.ilyaslabs.evl.EVL;

@EVL.DataModel(id = "order.order", name = "Order", storage = "postgres.orders", pii = true)
@EVL.Index(fields = {"customerId", "createdAt"}, reason = "Order history page")
public class Order {

    @EVL.Field(meaning = "Gross amount including tax", unit = "EUR", required = true)
    public long total;

    @EVL.Field(meaning = "Customer placing the order", sensitive = true)
    public String customerId;

    public String status;
}
