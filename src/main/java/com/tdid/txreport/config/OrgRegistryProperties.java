package com.tdid.txreport.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.tdid.txreport.domain.FeeSchedule;
import com.tdid.txreport.domain.FeeTier;
import com.tdid.txreport.domain.OrgProfile;

@ConfigurationProperties(prefix = "txreport")
public class OrgRegistryProperties {

    private List<OrgEntry> orgs = List.of();

    public List<OrgEntry> getOrgs() { return orgs; }
    public void setOrgs(List<OrgEntry> orgs) { this.orgs = orgs; }

    public Map<String, OrgProfile> buildRegistry() {
        return orgs.stream()
                .map(OrgEntry::toProfile)
                .collect(Collectors.toMap(OrgProfile::orgId, Function.identity()));
    }

    public List<OrgProfile> buildList() {
        return orgs.stream().map(OrgEntry::toProfile).toList();
    }

    // ---- nested binding classes ----

    public static class OrgEntry {
        private String orgId;
        private String displayName;
        private String gatewayId;
        private FeeScheduleEntry feeSchedule;

        public String getOrgId() { return orgId; }
        public void setOrgId(String orgId) { this.orgId = orgId; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getGatewayId() { return gatewayId; }
        public void setGatewayId(String gatewayId) { this.gatewayId = gatewayId; }

        public FeeScheduleEntry getFeeSchedule() { return feeSchedule; }
        public void setFeeSchedule(FeeScheduleEntry feeSchedule) { this.feeSchedule = feeSchedule; }

        OrgProfile toProfile() {
            FeeSchedule fs = null;
            if (feeSchedule != null) {
                List<FeeTier> tiers = feeSchedule.getTiers().stream()
                        .map(t -> new FeeTier(t.getFrom(), t.getTo(), t.getRate()))
                        .toList();
                fs = new FeeSchedule(feeSchedule.getMinimumTransactions(), tiers);
            }
            return new OrgProfile(orgId, displayName, gatewayId, fs);
        }
    }

    public static class FeeScheduleEntry {
        private long minimumTransactions;
        private List<TierEntry> tiers = List.of();

        public long getMinimumTransactions() { return minimumTransactions; }
        public void setMinimumTransactions(long minimumTransactions) { this.minimumTransactions = minimumTransactions; }

        public List<TierEntry> getTiers() { return tiers; }
        public void setTiers(List<TierEntry> tiers) { this.tiers = tiers; }
    }

    public static class TierEntry {
        private long from;
        private Long to;
        private BigDecimal rate;

        public long getFrom() { return from; }
        public void setFrom(long from) { this.from = from; }

        public Long getTo() { return to; }
        public void setTo(Long to) { this.to = to; }

        public BigDecimal getRate() { return rate; }
        public void setRate(BigDecimal rate) { this.rate = rate; }
    }
}
