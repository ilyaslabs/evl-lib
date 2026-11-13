package com.example.shop.order;

import io.github.ilyaslabs.evl.EVL;

@EVL.Component(id = "order.api", name = "Order API", layer = EVL.Layer.PRESENTATION)
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @EVL.Endpoint(id = "order.create", name = "Create Order", protocol = EVL.Protocol.HTTP, method = "POST",
        path = "/orders", requestType = CreateOrderRequest.class, flow = "order.placement")
    @EVL.ErrorResponse(id = "order.out-of-stock")
    public Order createOrder(CreateOrderRequest request) {
        return service.placeOrder(request);
    }
}
