package com.example.autosell;

import java.util.HashMap;
import java.util.Map;

public class AutoSellConfig {
    public boolean enabled = false;
    public int delayMs = 500;
    public boolean sellOnlyFullStacks = false;
    public boolean usePriceOverride = false;
    public int priceOverride = 0;

    public static final AutoSellConfig INSTANCE = new AutoSellConfig();

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", enabled);
        map.put("delayMs", delayMs);
        map.put("sellOnlyFullStacks", sellOnlyFullStacks);
        map.put("usePriceOverride", usePriceOverride);
        map.put("priceOverride", priceOverride);
        return map;
    }
}
