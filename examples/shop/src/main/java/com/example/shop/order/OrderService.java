package com.example.shop.order;

import com.example.shop.inventory.InventoryService;
import io.github.ilyaslabs.evl.EVL;

@EVL.Component(id = "order.service", name = "Order Service", layer = EVL.Layer.APPLICATION,
    responsibilities = {"Create orders", "Coordinate fulfilment"})
@EVL.Dependency(target = "inventory.service", type = EVL.DependencyType.API, reason = "Reserve stock before payment")
public class OrderService {

    private final InventoryService inventory;

    public OrderService(InventoryService inventory) {
        this.inventory = inventory;
    }

    @EVL.Flow(id = "order.placement", name = "Order Placement", category = EVL.Category.BUSINESS, domain = "sales",
        trigger = "Customer clicks Place order")
    @EVL.Doc(value = "Entry point for checkout.", requirements = "req.checkout-under-2s")
    @EVL.Producer(event = "order.placed", when = "Order confirmed")
    public Order placeOrder(CreateOrderRequest request) {
        validate(request);
        inventory.reserve(request.skus);
        return new Order();
    }

    @EVL.Step(flow = "order.placement", step = "validate")
    void validate(CreateOrderRequest request) {
        if (request.skus == null || request.skus.isEmpty()) {
            throw new IllegalArgumentException("empty order");
        }
    }

    @EVL.Step(flow = "order.placement", step = "confirm")
    void confirm(Order order) {
        order.status = "PLACED";
    }
}
