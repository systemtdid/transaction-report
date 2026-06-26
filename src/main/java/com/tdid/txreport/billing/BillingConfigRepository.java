package com.tdid.txreport.billing;

import java.util.Optional;

/**
 * Lookup for per-org billing configuration. The default implementation loads from
 * a JSON resource into memory; a DB-backed implementation (e.g. the {@code feereport}
 * table) can replace it without touching {@link BillingEngineService}.
 * See ARCHITECTURE.md §8.5.3.
 */
public interface BillingConfigRepository {

    Optional<OrgBillingConfig> findByOrgId(String orgId);
}
