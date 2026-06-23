package com.tdid.txreport.domain;

public record OrgProfile(
        String orgId,
        String displayName,
        String gatewayId,
        FeeSchedule feeSchedule) {

    public boolean hasGatewayFilter() {
        return gatewayId != null && !gatewayId.isBlank();
    }

    public boolean hasFeeSchedule() {
        return feeSchedule != null;
    }
}
