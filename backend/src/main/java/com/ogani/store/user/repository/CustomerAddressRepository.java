package com.ogani.store.user.repository;

import com.ogani.store.user.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress,Long> {
    Optional<CustomerAddress> findByIdAndUserId(Long id, Long userId);
    List<CustomerAddress> findByUserIdOrderByDefaultAddressDescIdAsc(Long userId);
}
