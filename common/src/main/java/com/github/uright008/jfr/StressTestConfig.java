package com.github.uright008.jfr;

import com.google.gson.JsonObject;
import com.github.uright008.pc.ParallelConfig;

public final class StressTestConfig extends ParallelConfig {

    private static final StressTestConfig INSTANCE = new StressTestConfig();

    private volatile int timeoutSeconds;
    private volatile boolean waitForPlayer;
    private volatile boolean hopperEnabled;
    private volatile int chunkRadius;
    private volatile int tntSize;
    private volatile boolean entitySpawnEnabled;
    private volatile int entitySpawnRate;
    private volatile int entitySpawnRadius;

    private StressTestConfig() {
        super("stress-test");
    }

    public static void init() { INSTANCE.initialize(); }
    public static boolean isEnabled() { return INSTANCE.loaded && INSTANCE.timeoutSeconds > 0; }
    public static boolean isHopperEnabled() { return INSTANCE.loaded && INSTANCE.hopperEnabled; }
    public static int timeoutSeconds() { return INSTANCE.timeoutSeconds; }
    public static boolean waitForPlayer() { return INSTANCE.waitForPlayer; }
    public static int chunkRadius() { return INSTANCE.chunkRadius; }
    public static int tntSize() { return INSTANCE.tntSize; }
    public static boolean isEntitySpawnEnabled() { return INSTANCE.loaded && INSTANCE.entitySpawnEnabled; }
    public static int entitySpawnRate() { return INSTANCE.entitySpawnRate; }
    public static int entitySpawnRadius() { return INSTANCE.entitySpawnRadius; }

    @Override
    protected void applyDefaults() {
        timeoutSeconds = 30;
        waitForPlayer = false;
        hopperEnabled = false;
        chunkRadius = 1;
        tntSize = 3;
        entitySpawnEnabled = false;
        entitySpawnRate = 10;
        entitySpawnRadius = 4;
    }

    @Override
    protected void read(JsonObject json) {
        applyDefaults();
        if (json.has("timeoutSeconds")) timeoutSeconds = json.get("timeoutSeconds").getAsInt();
        if (json.has("waitForPlayer")) waitForPlayer = json.get("waitForPlayer").getAsBoolean();
        if (json.has("hopperEnabled")) hopperEnabled = json.get("hopperEnabled").getAsBoolean();
        if (json.has("chunkRadius")) chunkRadius = Math.max(1, json.get("chunkRadius").getAsInt());
        if (json.has("tntSize")) tntSize = Math.max(1, Math.min(5, json.get("tntSize").getAsInt()));
        if (json.has("entitySpawnEnabled")) entitySpawnEnabled = json.get("entitySpawnEnabled").getAsBoolean();
        if (json.has("entitySpawnRate")) entitySpawnRate = Math.max(1, json.get("entitySpawnRate").getAsInt());
        if (json.has("entitySpawnRadius")) entitySpawnRadius = Math.max(1, json.get("entitySpawnRadius").getAsInt());
        int chunks = (chunkRadius * 2) * (chunkRadius * 2);
        logger().info("Stress test: TNT={} hopper={} entitySpawn={} (rate={}/tick, radius={})",
                timeoutSeconds > 0 ? "ON" : "OFF", hopperEnabled ? "ON" : "OFF",
                entitySpawnEnabled ? "ON" : "OFF", entitySpawnRate, entitySpawnRadius);
    }

    @Override
    protected JsonObject write() {
        JsonObject json = new JsonObject();
        json.addProperty("timeoutSeconds", timeoutSeconds);
        json.addProperty("waitForPlayer", waitForPlayer);
        json.addProperty("hopperEnabled", hopperEnabled);
        json.addProperty("chunkRadius", chunkRadius);
        json.addProperty("tntSize", tntSize);
        json.addProperty("entitySpawnEnabled", entitySpawnEnabled);
        json.addProperty("entitySpawnRate", entitySpawnRate);
        json.addProperty("entitySpawnRadius", entitySpawnRadius);
        return json;
    }
}
