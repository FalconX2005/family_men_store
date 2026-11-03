package uz.familymens.store.service;

import uz.familymens.store.model.CustomerOrder;

public record OrderPlacedEvent(CustomerOrder order) {
}
