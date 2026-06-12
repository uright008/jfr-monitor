package com.github.uright008.jfr;

import com.mojang.brigadier.StringReader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Automated JFR profiling on server start.
 *
 * <p>When {@code autoProfile: true} in {@code config/mc-parallel.json}:
 * <ol>
 *   <li>Waits {@code warmupSeconds} for world loading</li>
 *   <li>Spawns a Carpet spectator fake player {@code jfr-bot}</li>
 *   <li>Starts {@code recordSeconds} JFR recording</li>
 *   <li>Dumps the recording and shuts down the server</li>
 * </ol>
 * Output lands in {@code run/jfr/}.
 */
public final class JfrAutoProfiler {

    private static final Logger LOG = LoggerFactory.getLogger("mc-parallel:jfr-auto");
    private static final String BOT_NAME = "jfr-bot";

    private JfrAutoProfiler() {}

    public static void register() {
        if (!JfrProfileConfig.isAutoProfile()) return;

        ServerLifecycleEvents.SERVER_STARTED.register(JfrAutoProfiler::onServerStarted);
        LOG.info("JFR auto-profile registered");
    }

    private static void onServerStarted(MinecraftServer server) {
        int warmup = JfrProfileConfig.warmupSeconds();
        int record = JfrProfileConfig.recordSeconds();

        LOG.info("Auto-profile: warming up {}s, will record {}s", warmup, record);

        Thread profileThread = new Thread(() -> {
            try {
                // ── Spawn Carpet bot (creates the "player" for stress test) ──
                spawnBot(server);

                // ── Warmup ──
                Thread.sleep(TimeUnit.SECONDS.toMillis(warmup));
                logMspt(server, "pre-record");

                // ── Start spark profiler ──
                runCommand(server, "spark profiler start --timeout " + record + " --thread *");
                LOG.info("Spark profiler started ({} seconds)", record);

                // ── Wait for recording ──
                Thread.sleep(TimeUnit.SECONDS.toMillis(record + 2));

                // ── Log MSPT after recording ──
                logMspt(server, "post-record");

                // ── Shutdown ──
                LOG.info("Auto-profile: shutting down server");
                server.halt(false);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.error("Auto-profile failed", e);
                server.halt(false);
            }
        }, "jfr-auto-profile");
        profileThread.setDaemon(true);
        profileThread.start();
    }

    private static void spawnBot(MinecraftServer server) {
        try {
            int x = JfrProfileConfig.botX();
            int y = JfrProfileConfig.botY();
            int z = JfrProfileConfig.botZ();
            runCommand(server, "player " + BOT_NAME + " spawn at " + x + " " + y + " " + z
                    + " in minecraft:overworld");
            runCommand(server, "player " + BOT_NAME + " gamemode spectator");
            LOG.info("Spawned Carpet bot '{}' at {},{},{}", BOT_NAME, x, y, z);

            String postCmd = JfrProfileConfig.postSpawnCommand();
            if (postCmd != null && !postCmd.isEmpty()) {
                runCommand(server, postCmd);
                LOG.info("Ran post-spawn command: {}", postCmd);
            }
        } catch (Exception e) {
            LOG.warn("Failed to spawn Carpet bot (is Carpet installed?)", e);
        }
    }

    private static void runCommand(MinecraftServer server, String cmd) {
        var source = server.createCommandSourceStack().withSuppressedOutput();
        var parse = server.getCommands().getDispatcher()
                .parse(new StringReader(cmd), source);
        server.getCommands().performCommand(parse, cmd);
    }

    private static void logMspt(MinecraftServer server, String tag) {
        long avgNs = server.getAverageTickTimeNanos();
        int tickCount = server.getTickCount();
        double avgMs = avgNs / 1_000_000.0;
        double tps = avgMs > 0 ? 1000.0 / avgMs : 20.0;
        LOG.info("MSPT[{}]: {} ticks, avg {} ms/tick, {} TPS",
                tag, tickCount, String.format("%.1f", avgMs), String.format("%.1f", tps));
    }
}
