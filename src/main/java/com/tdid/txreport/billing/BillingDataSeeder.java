package com.tdid.txreport.billing;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads {@code classpath:billing/org-billing-config.json} at startup, assigns tier
 * numbers by order, validates each config, and populates the repository.
 * Fails fast on a structurally invalid config. See ARCHITECTURE.md §8.5.3.
 */
@Component
public class BillingDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(BillingDataSeeder.class);
    private static final String RESOURCE = "billing/org-billing-config.json";

    private final InMemoryBillingConfigRepository repository;
    private final ObjectMapper objectMapper;

    public BillingDataSeeder(InMemoryBillingConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void seed() {
        List<RawOrg> raw = readResource();
        List<OrgBillingConfig> configs = new ArrayList<>(raw.size());
        Set<String> seenOrgIds = new HashSet<>();
        for (RawOrg o : raw) {
            OrgBillingConfig config = toConfig(o);
            validate(config);
            if (!seenOrgIds.add(config.orgId())) {
                throw new IllegalStateException("Duplicate billing orgId: " + config.orgId());
            }
            configs.add(config);
        }
        repository.replaceAll(configs);
        log.info("Loaded {} organisation billing config(s) from {}", configs.size(), RESOURCE);
    }

    private List<RawOrg> readResource() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<RawOrg>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load billing config: " + RESOURCE, e);
        }
    }

    private OrgBillingConfig toConfig(RawOrg o) {
        List<FeeTier> tiers = new ArrayList<>(o.tiers().size());
        for (int i = 0; i < o.tiers().size(); i++) {
            RawTier t = o.tiers().get(i);
            tiers.add(new FeeTier(i + 1, t.maxCapacity(), t.rate()));
        }
        return new OrgBillingConfig(
                o.orgId(), o.orgName(), o.minimumFee(), o.fixedMonthlyFee(),
                o.waiveIfBelowMinimum(), List.copyOf(tiers));
    }

    /** Fail fast on structurally invalid configs (ARCHITECTURE.md §8.5.3). */
    private void validate(OrgBillingConfig c) {
        if (c.orgId() == null || c.orgId().isBlank()) {
            throw new IllegalStateException("Billing config with missing orgId");
        }
        List<FeeTier> tiers = c.tiers();
        if (tiers.isEmpty()) {
            throw new IllegalStateException("Org " + c.orgId() + " has no tiers");
        }
        int unbounded = 0;
        for (int i = 0; i < tiers.size(); i++) {
            FeeTier t = tiers.get(i);
            if (t.rate() == null || t.rate().signum() < 0) {
                throw new IllegalStateException("Org " + c.orgId() + " tier " + (i + 1) + " has an invalid rate");
            }
            if (t.isUnbounded()) {
                unbounded++;
                if (i != tiers.size() - 1) {
                    throw new IllegalStateException("Org " + c.orgId() + ": the unbounded tier must be last");
                }
            } else if (t.maxCapacity() <= 0) {
                throw new IllegalStateException("Org " + c.orgId() + " tier " + (i + 1) + " has non-positive capacity");
            }
        }
        if (unbounded != 1) {
            throw new IllegalStateException(
                    "Org " + c.orgId() + " must have exactly one unbounded (null) tier; found " + unbounded);
        }
    }

    // ---- internal JSON shapes; tierNumber is assigned by the seeder, not the file ----
    record RawOrg(String orgId, String orgName, BigDecimal minimumFee,
                  BigDecimal fixedMonthlyFee, boolean waiveIfBelowMinimum, List<RawTier> tiers) {}

    record RawTier(Long maxCapacity, BigDecimal rate) {}
}
