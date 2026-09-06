package com.rezhub.reservation.payment;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

public class PricingPolicyTest {

    @Test
    public void validate_acceptsCommitmentWindowComfortablyUnderCap() {
        var policy = new PricingPolicy(5000, "eur", 0.10, Duration.ofDays(2));

        assertThatCode(policy::validate).doesNotThrowAnyException();
    }

    @Test
    public void validate_rejectsCommitmentWindowOverCap() {
        var policy = new PricingPolicy(5000, "eur", 0.10, Duration.ofDays(10));

        assertThatThrownBy(policy::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void validate_rejectsNonPositivePrice() {
        var policy = new PricingPolicy(0, "eur", 0.10, Duration.ofDays(1));

        assertThatThrownBy(policy::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void validate_rejectsCommissionFractionOutOfRange() {
        var policy = new PricingPolicy(5000, "eur", 1.5, Duration.ofDays(1));

        assertThatThrownBy(policy::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void validate_rejectsZeroOrNegativeCommitmentWindow() {
        var policy = new PricingPolicy(5000, "eur", 0.10, Duration.ZERO);

        assertThatThrownBy(policy::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
