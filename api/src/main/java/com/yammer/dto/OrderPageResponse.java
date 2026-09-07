package com.yammer.dto;

import java.util.List;

/** One server-side page of the orders report. */
public record OrderPageResponse(List<OrderReportRow> content, long total, int page, int size) {
}
