package com.tdid.txreport.domain;

/** An organisation as far as report generation is concerned. Billing now comes from the
 *  Billing Engine (keyed by orgId), so the profile no longer carries a fee schedule. */
public record OrgProfile(
        String orgId,
        String displayName,
        String gatewayId) {

    public boolean hasGatewayFilter() {
        return gatewayId != null && !gatewayId.isBlank();
    }
}
