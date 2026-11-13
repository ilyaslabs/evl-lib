package com.example.shop.inventory;

import io.github.ilyaslabs.evl.EVL;
import java.util.List;

@EVL.Component(id = "inventory.service", name = "Inventory Service", layer = EVL.Layer.DOMAIN)
public class InventoryService {

    @EVL.Flow(id = "inventory.reserve", name = "Reserve Inventory", category = EVL.Category.TECHNICAL)
    @EVL.Consumer(event = "order.placed", idempotent = true)
    public boolean reserve(List<String> skus) {
        return !skus.isEmpty();
    }

    @EVL.Flow(id = "inventory.release", name = "Release Stock", category = EVL.Category.TECHNICAL)
    public void release(List<String> skus) {
        // returns reserved stock
    }
}
