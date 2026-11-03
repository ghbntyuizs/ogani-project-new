package com.ogani.store.order.repository;

import com.ogani.store.content.entity.Setting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CheckoutSettingsRepository extends JpaRepository<Setting,String> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from Setting s where s.settingKey in ('shipping_fee','free_shipping_threshold','currency') order by s.settingKey")
    List<Setting> findCheckoutSettings();
}
