package com.tdid.txreport.billing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

/**
 * In-memory {@link BillingConfigRepository}, populated once at startup by
 * {@link BillingDataSeeder}. See ARCHITECTURE.md §8.5.3.
 */
@Repository
public class InMemoryBillingConfigRepository implements BillingConfigRepository {

    private final Map<String, OrgBillingConfig> byOrgId = new ConcurrentHashMap<>();

    /** Replace all configs (called by the seeder at startup). */
    void replaceAll(List<OrgBillingConfig> configs) {
        byOrgId.clear();
        for (OrgBillingConfig c : configs) {
            byOrgId.put(c.orgId(), c);
        }
    }

    @Override
    public Optional<OrgBillingConfig> findByOrgId(String orgId) {
        return Optional.ofNullable(byOrgId.get(orgId));
    }
}
