package com.yammer.dto;

import java.util.UUID;

/** Result of a customer joining a table: the browser-stored token and its status. */
public record CustomerJoinResponse(UUID token, String status) {
}
