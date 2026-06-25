package com.tdid.txreport.config;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.tdid.txreport.domain.OrgProfile;

/**
 * Optional per-org overrides from application.yml (display name + gateway filter).
 * Billing configuration lives separately in the Billing Engine (see {@code billing/}).
 */
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

    public static class OrgEntry {
        private String orgId;
        private String displayName;
        private String gatewayId;

        public String getOrgId() { return orgId; }
        public void setOrgId(String orgId) { this.orgId = orgId; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getGatewayId() { return gatewayId; }
        public void setGatewayId(String gatewayId) { this.gatewayId = gatewayId; }

        OrgProfile toProfile() {
            return new OrgProfile(orgId, displayName, gatewayId, null);
        }
    }
}
