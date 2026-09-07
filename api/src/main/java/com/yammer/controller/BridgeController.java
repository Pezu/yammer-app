package com.yammer.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stub until the on-prem bridge (fiscal registers + printers over WebSocket) is
 * ported from the old project. The peripherals page polls this for its USB
 * device picker; an empty list simply means "no bridge connected".
 */
@RestController
@RequestMapping("/bridge")
public class BridgeController {

    /** Currently connected bridge devices — feeds the peripherals page's USB device picker. */
    @GetMapping("/devices")
    public List<Object> devices() {
        return List.of();
    }
}
