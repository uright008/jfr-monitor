package com.github.uright008.jfr;

import com.google.gson.JsonObject;
import com.github.uright008.pc.ParallelConfig;

public final class StressTestConfig extends ParallelConfig {

    private static final StressTestConfig INSTANCE = new StressTestConfig();

    private volatile int timeoutSeconds;
    private volatile boolean waitForPlayer;
    private volatile boolean hopperEnabled;

    private StressTestConfig() {
        super("stress-test");
    }

    public static void init() {
        INSTANCE.initialize();
    }

    public static boolean isEnabled() { return INSTANCE.loaded && INSTANCE.timeoutSeconds > 0; }
    public static boolean isHopperEnabled() { return INSTANCE.loaded && INSTANCE.hopperEnabled; }
    public static int timeoutSeconds() { return INSTANCE.timeoutSeconds; }
    public static boolean waitForPlayer() { return INSTANCE.waitForPlayer; }

    @Override
    protected void applyDefaults() {
        timeoutSeconds = 30;
        waitForPlayer = false;
        hopperEnabled = false;
    }

    @Override
    protected void read(JsonObject json) {
        applyDefaults();
        if (json.has("timeoutSeconds")) timeoutSeconds = json.get("timeoutSeconds").getAsInt();
        if (json.has("waitForPlayer")) waitForPlayer = json.get("waitForPlayer").getAsBoolean();
        if (json.has("hopperEnabled")) hopperEnabled = json.get("hopperEnabled").getAsBoolean();
        logger().info("TNT stress test: {} ({}s, waitForPlayer={}), hopper: {}",
                timeoutSeconds > 0 ? "ON" : "OFF", timeoutSeconds, waitForPlayer, hopperEnabled ? "ON" : "OFF");
    }

    @Override
    protected JsonObject write() {
        JsonObject json = new JsonObject();
        json.addProperty("timeoutSeconds", timeoutSeconds);
        json.addProperty("waitForPlayer", waitForPlayer);
        json.addProperty("hopperEnabled", hopperEnabled);
        return json;
    }
}
