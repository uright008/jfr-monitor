package com.github.uright008.tester;

import com.google.gson.JsonObject;
import com.github.uright008.pc.ParallelConfig;

public final class StressTestConfig extends ParallelConfig {

    private static final StressTestConfig INSTANCE = new StressTestConfig();

    private volatile int timeoutSeconds;
    private volatile boolean waitForPlayer;
    private volatile String mode;          // "" | "tnt" | "hopper" | "entity"
    private volatile int chunkRadius;
    private volatile int tntSize;
    private volatile int entitySpawnRate;

    private StressTestConfig() { super("stress-test"); }

    public static void init() { INSTANCE.initialize(); }

    public static boolean isActive() { return INSTANCE.loaded && !INSTANCE.mode.isEmpty(); }
    public static boolean isTnt() { return INSTANCE.loaded && "tnt".equals(INSTANCE.mode); }
    public static boolean isHopper() { return INSTANCE.loaded && "hopper".equals(INSTANCE.mode); }
    public static boolean isEntity() { return INSTANCE.loaded && "entity".equals(INSTANCE.mode); }

    public static int timeoutSeconds() { return INSTANCE.timeoutSeconds; }
    public static boolean waitForPlayer() { return INSTANCE.waitForPlayer; }
    public static int chunkRadius() { return INSTANCE.chunkRadius; }
    public static int tntSize() { return INSTANCE.tntSize; }
    public static int entitySpawnRate() { return INSTANCE.entitySpawnRate; }

    public static String getMode() { return INSTANCE.mode; }
    public static void setMode(String v) { INSTANCE.mode = v; INSTANCE.save(); }
    public static void setTimeout(int v) { INSTANCE.timeoutSeconds = v; INSTANCE.save(); }
    public static void setChunkRadius(int v) { INSTANCE.chunkRadius = Math.max(1, v); INSTANCE.save(); }
    public static void setTntSize(int v) { INSTANCE.tntSize = Math.max(1, Math.min(5, v)); INSTANCE.save(); }
    public static void setEntitySpawnRate(int v) { INSTANCE.entitySpawnRate = Math.max(1, v); INSTANCE.save(); }

    @Override
    protected void applyDefaults() {
        timeoutSeconds = 30;
        waitForPlayer = false;
        mode = "";
        chunkRadius = 1;
        tntSize = 3;
        entitySpawnRate = 10;
    }

    @Override
    protected void read(JsonObject json) {
        applyDefaults();
        if (json.has("timeoutSeconds")) timeoutSeconds = json.get("timeoutSeconds").getAsInt();
        if (json.has("waitForPlayer")) waitForPlayer = json.get("waitForPlayer").getAsBoolean();
        if (json.has("mode")) mode = json.get("mode").getAsString();
        if (json.has("chunkRadius")) chunkRadius = Math.max(1, json.get("chunkRadius").getAsInt());
        if (json.has("tntSize")) tntSize = Math.max(1, Math.min(5, json.get("tntSize").getAsInt()));
        if (json.has("entitySpawnRate")) entitySpawnRate = Math.max(1, json.get("entitySpawnRate").getAsInt());
        logger().info("Stress test: mode={} timeout={}s chunkRadius={}", mode.isEmpty() ? "OFF" : mode, timeoutSeconds, chunkRadius);
    }

    @Override
    protected JsonObject write() {
        JsonObject json = new JsonObject();
        json.addProperty("timeoutSeconds", timeoutSeconds);
        json.addProperty("waitForPlayer", waitForPlayer);
        json.addProperty("mode", mode);
        json.addProperty("chunkRadius", chunkRadius);
        json.addProperty("tntSize", tntSize);
        json.addProperty("entitySpawnRate", entitySpawnRate);
        return json;
    }
}
