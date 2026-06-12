package com.github.uright008.jfr;

import com.github.uright008.pc.ServerHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public final class JfrAutoProfiler {

    private static final Logger LOG = LoggerFactory.getLogger("mc-parallel:jfr-auto");

    private JfrAutoProfiler() {}

    public static void register() {
        if (!JfrProfileConfig.isAutoProfile()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(JfrAutoProfiler::onServerStarted);
        LOG.info("Auto-profile registered");
    }

    private static void onServerStarted(MinecraftServer server) {
        int warmup = JfrProfileConfig.warmupSeconds();
        int record = JfrProfileConfig.recordSeconds();
        LOG.info("Auto-profile: warmup {}s, record {}s", warmup, record);

        Thread profileThread = new Thread(() -> {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(warmup));

                LOG.info(ServerHelper.msptLog(server, "pre-record"));

                ServerHelper.runCommand(server, "spark profiler start --timeout " + record + " --thread *");

                Thread.sleep(TimeUnit.SECONDS.toMillis(record + 2));

                LOG.info(ServerHelper.msptLog(server, "post-record"));
                LOG.info("Auto-profile: shutting down");
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
}
