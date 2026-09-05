package com.rezhub.reservation.customer.facility.dto;

import java.util.Optional;
import java.util.Set;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.payment.PricingPolicy;

public record Facility(String name, Address address, String timezone, String botToken,
                       Set<String> adminUserIds, Optional<PricingPolicy> pricingPolicy,
                       Optional<String> stripeConnectedAccountId) {

    /** Backward-compatible constructor for callers that predate PricingPolicy/Stripe fields. */
    public Facility(String name, Address address, String timezone, String botToken, Set<String> adminUserIds) {
        this(name, address, timezone, botToken, adminUserIds, Optional.empty(), Optional.empty());
    }
}
