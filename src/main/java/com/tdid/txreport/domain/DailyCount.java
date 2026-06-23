package com.tdid.txreport.domain;

import java.time.LocalDate;

public record DailyCount(LocalDate date, long count) {
}
