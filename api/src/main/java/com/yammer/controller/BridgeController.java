package com.yammer.controller;

import com.yammer.ws.BridgeWsHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bridge")
@RequiredArgsConstructor
public class BridgeController {

    private final BridgeWsHandler bridgeWsHandler;

    /** Currently connected bridge devices — feeds the peripherals page's USB device picker. */
    @GetMapping("/devices")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER')")
    public List<BridgeWsHandler.ConnectedDevice> devices() {
        return bridgeWsHandler.connectedDevices();
    }
}
