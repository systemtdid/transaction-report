package com.tdid.txreport.billing;

import java.util.LinkedHashMap;
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

    /** Replace all configs (called by the seeder). Preserves insertion order for {@link #findAll()}. */
    void replaceAll(List<OrgBillingConfig> configs) {
        Map<String, OrgBillingConfig> next = new LinkedHashMap<>();
        for (OrgBillingConfig c : configs) {
            next.put(c.orgId(), c);
        }
        byOrgId.clear();
        byOrgId.putAll(next);
    }

    @Override
    public Optional<OrgBillingConfig> findByOrgId(String orgId) {
        return Optional.ofNullable(byOrgId.get(orgId));
    }

    @Override
    public List<OrgBillingConfig> findAll() {
        return List.copyOf(byOrgId.values());
    }
}
