package com.tdid.txreport.domain;

import java.math.BigDecimal;

public record FeeTier(long from, Long to, BigDecimal rate) {

    public boolean contains(long position) {
        return position >= from && (to == null || position <= to);
    }

    public String displayRange() {
        if (to == null) {
            return String.format("%,d ++", from);
        }
        return String.format("%,d - %,d", from, to);
    }
}
