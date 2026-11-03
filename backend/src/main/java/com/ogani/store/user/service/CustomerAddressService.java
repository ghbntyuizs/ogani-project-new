package com.ogani.store.user.service;

import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.response.checkout.SavedAddressResponse;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.dto.request.CreateAddressRequest;
import com.ogani.store.user.entity.CustomerAddress;
import com.ogani.store.user.entity.User;
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
        var customer=owner(principal);
        validate(request);
        if(addresses.countByUserId(customer.getId())>=10)
            throw AppException.withCustomMessage(ErrorCode.ADDRESS_LIMIT_EXCEEDED,"A customer can save at most 10 addresses");
        boolean makeDefault=request.defaultAddress() || !addresses.existsByUserIdAndDefaultAddressTrue(customer.getId());
        if(makeDefault) addresses.clearDefault(customer.getId());
        var address=new CustomerAddress();
        address.setUser(customer);apply(address,request);address.setDefaultAddress(makeDefault);
        addresses.saveAndFlush(address);
        return response(address);
    }

    @Transactional
    public SavedAddressResponse update(UserPrincipal principal,Long id,CreateAddressRequest request) {
        var customer=owner(principal);
        if(id==null || id<=0) throw new AppException(ErrorCode.VALIDATION_ERROR);
        validate(request);
        var address=addresses.findByIdAndUserId(id,customer.getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Address"));
        boolean makeDefault=request.defaultAddress() || Boolean.TRUE.equals(address.getDefaultAddress())
                || !addresses.existsByUserIdAndDefaultAddressTrue(customer.getId());
        // Exclude the managed target so an unchanged default cannot be cleared by bulk SQL.
        if(makeDefault) addresses.clearOtherDefaults(customer.getId(),id);
        apply(address,request);address.setDefaultAddress(makeDefault);
        addresses.flush();
        return response(address);
    }

    private User owner(UserPrincipal principal) {
        if(principal==null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        var customer=users.findByIdForCartUpdate(principal.getId()).orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if(customer.getStatus()!=UserStatus.ACTIVE) throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        if(customer.getRole()==null || !"CUSTOMER".equals(customer.getRole().getName())) throw new AppException(ErrorCode.FORBIDDEN);
        return customer;
    }

    @Transactional
    public void delete(UserPrincipal principal,Long id) {
        var customer=owner(principal);
        var address=ownedAddress(id,customer.getId());
        addresses.delete(address);
        // Hibernate can order updates before deletes: release the unique default first.
        addresses.flush();
        if(!addresses.existsByUserIdAndDefaultAddressTrue(customer.getId())) {
            addresses.findFirstByUserIdOrderByIdAsc(customer.getId()).ifPresent(replacement -> {
                replacement.setDefaultAddress(true);
                addresses.flush();
            });
        }
    }

    @Transactional
    public SavedAddressResponse setDefault(UserPrincipal principal,Long id) {
        var customer=owner(principal);
        var address=ownedAddress(id,customer.getId());
        addresses.clearOtherDefaults(customer.getId(),id);
        address.setDefaultAddress(true);
        addresses.flush();
        return response(address);
    }

    private CustomerAddress ownedAddress(Long id,Long userId) {
        if(id==null || id<=0) throw new AppException(ErrorCode.VALIDATION_ERROR);
        return addresses.findByIdAndUserId(id,userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Address"));
    }

    private void validate(CreateAddressRequest request) {
        if(request==null || !validator.validate(request).isEmpty()) throw new AppException(ErrorCode.VALIDATION_ERROR);
    }

    private void apply(CustomerAddress address,CreateAddressRequest request) {
        address.setRecipientName(request.recipientName());address.setPhone(request.phone());
        address.setCity(request.city());address.setDistrict(request.district());address.setWard(request.ward());
        address.setAddressLine(request.address());
    }

    private SavedAddressResponse response(CustomerAddress address) {
        return new SavedAddressResponse(address.getId(),address.getRecipientName(),address.getPhone(),address.getCountry(),
                address.getAddressLine(),address.getCity(),address.getState(),address.getPostcode(),Boolean.TRUE.equals(address.getDefaultAddress()),address.getDistrict(),address.getWard());
    }
}
