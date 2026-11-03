package com.ogani.store.user.service;

import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.response.checkout.SavedAddressResponse;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.dto.request.CreateAddressRequest;
import com.ogani.store.user.entity.CustomerAddress;
import com.ogani.store.user.entity.UserStatus;
import com.ogani.store.user.repository.CustomerAddressRepository;
import com.ogani.store.user.repository.UserRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerAddressService {
    private final UserRepository users;
    private final CustomerAddressRepository addresses;
    private final Validator validator;

    @Transactional
    public SavedAddressResponse create(UserPrincipal principal,CreateAddressRequest request) {
        if(principal==null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        var customer=users.findByIdForCartUpdate(principal.getId()).orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if(customer.getStatus()!=UserStatus.ACTIVE) throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        if(customer.getRole()==null || !"CUSTOMER".equals(customer.getRole().getName())) throw new AppException(ErrorCode.FORBIDDEN);
        if(request==null || !validator.validate(request).isEmpty()) throw new AppException(ErrorCode.VALIDATION_ERROR);
        if(addresses.countByUserId(customer.getId())>=10)
            throw AppException.withCustomMessage(ErrorCode.ADDRESS_LIMIT_EXCEEDED,"A customer can save at most 10 addresses");
        boolean makeDefault=request.defaultAddress() || !addresses.existsByUserIdAndDefaultAddressTrue(customer.getId());
        if(makeDefault) addresses.clearDefault(customer.getId());
        var address=new CustomerAddress();
        address.setUser(customer);address.setRecipientName(request.recipientName());address.setPhone(request.phone());
        address.setCity(request.city());address.setDistrict(request.district());address.setWard(request.ward());
        address.setAddressLine(request.address());address.setDefaultAddress(makeDefault);
        addresses.saveAndFlush(address);
        return new SavedAddressResponse(address.getId(),address.getRecipientName(),address.getPhone(),address.getCountry(),
                address.getAddressLine(),address.getCity(),address.getState(),address.getPostcode(),makeDefault,address.getDistrict(),address.getWard());
    }
}
