package uz.familymens.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.familymens.store.model.CustomerOrder;
import uz.familymens.store.model.OrderStatus;

import java.util.List;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findAllByOrderByCreatedAtDesc();

    long countByStatus(OrderStatus status);
}
