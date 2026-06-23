package com.tdid.txreport.domain;

import java.util.List;

public record FeeSchedule(long minimumTransactions, List<FeeTier> tiers) {
}
