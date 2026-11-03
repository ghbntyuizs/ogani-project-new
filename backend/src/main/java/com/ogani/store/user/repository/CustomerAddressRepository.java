package com.ogani.store.user.repository;

import com.ogani.store.user.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress,Long> {
    Optional<CustomerAddress> findByIdAndUserId(Long id, Long userId);
    List<CustomerAddress> findByUserIdOrderByDefaultAddressDescIdAsc(Long userId);
    long countByUserId(Long userId);
    boolean existsByUserIdAndDefaultAddressTrue(Long userId);
    @Modifying
    @Query("update CustomerAddress a set a.defaultAddress=false where a.user.id=:userId and a.defaultAddress=true")
    int clearDefault(Long userId);
    @Modifying
    @Query("update CustomerAddress a set a.defaultAddress=false where a.user.id=:userId and a.id<>:addressId and a.defaultAddress=true")
    int clearOtherDefaults(Long userId,Long addressId);
}
