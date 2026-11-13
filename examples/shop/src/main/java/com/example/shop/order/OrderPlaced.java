package com.example.shop.order;

import io.github.ilyaslabs.evl.EVL;

@EVL.Event(id = "order.placed", name = "Order Placed", channel = "orders.placed",
    delivery = EVL.Delivery.AT_LEAST_ONCE)
public class OrderPlaced {
    public String orderId;
}
