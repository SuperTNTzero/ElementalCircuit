package dev.elementalcircuits.server.probe;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.application.NodeSurvey;
import dev.elementalcircuits.application.observation.SurveyReport;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.survey.SurveyArea;
import dev.elementalcircuits.network.ProbeNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;

import java.lang.ref.WeakReference;
import java.util.*;

/** Per-world bounded scan scheduler; sessions are transient observations, never saved gameplay. */
public final class ProbeService {
    private static final Map<ServerLevel, ProbeService> WORLDS = new WeakHashMap<>();

    private record Job(
            UUID token,
            WeakReference<ServerPlayer> player,
            SurveyArea area,
            NodeSurvey scan,
            long started,
            Map<Long, Boolean> regions) {}

    private final Deque<Job> jobs = new ArrayDeque<>();

    private ProbeService() {}

    private static ProbeService get(ServerLevel level) {
        return WORLDS.computeIfAbsent(level, k -> new ProbeService());
    }

    public static int running(ServerLevel level) {
        var service = WORLDS.get(level);
        return service == null ? 0 : service.jobs.size();
    }

    public static NodeSurvey.Progress progress(ServerPlayer p) {
        var s = WORLDS.get(p.serverLevel());
        if (s != null) for (var j : s.jobs) if (j.player.get() == p) return j.scan.progress();
        return null;
    }

    public static void use(ServerPlayer p, InteractionHand hand, BlockPos target) {
        if (!p.isAlive()
                || !p.getItemInHand(hand).is(ElementalCircuits.PROBE_WAND.get())
                || p.getCooldowns().isOnCooldown(ElementalCircuits.PROBE_WAND.get())) return;
        p.getCooldowns().addCooldown(ElementalCircuits.PROBE_WAND.get(), 10);
        try {
            if (target != null) {
                if (!p.serverLevel().hasChunkAt(target)
                        || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 64
                        || !p.serverLevel().mayInteract(p, target))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.probe._probe_service.001"));
                var b = p.serverLevel().getBlockEntity(target);
                if (b instanceof CircuitBlockEntity
                        || b instanceof ManaNodeBlockEntity
                        || b instanceof WorkstationBlockEntity
                        || p.serverLevel()
                                .getBlockState(target)
                                .is(ElementalCircuits.WAND_WORKBENCH.get())
                        || p.isShiftKeyDown()) {
                    open(p, ProbeReports.block(p.serverLevel(), target));
                    return;
                }
            }
            if (target == null && p.isShiftKeyDown()) {
                var other =
                        p.getItemInHand(
                                hand == InteractionHand.MAIN_HAND
                                        ? InteractionHand.OFF_HAND
                                        : InteractionHand.MAIN_HAND);
                if (!ProbeReports.carrier(other))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.probe._probe_service.002"));
                open(p, ProbeReports.item(p.serverLevel(), p.blockPosition(), other));
                return;
            }
            get(p.serverLevel()).begin(p);
        } catch (RuntimeException error) {
            p.displayClientMessage(
                    Component.literal(
                            error.getMessage() == null
                                    ? dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.probe._probe_service.003")
                                    : error.getMessage()),
                    true);
        }
    }

    private static void open(ServerPlayer p, SurveyReport report) {
        ProbeNetwork.send(p, UUID.randomUUID(), report, true);
    }

    private void begin(ServerPlayer p) {
        for (var job : jobs)
            if (job.player.get() == p) {
                ProbeNetwork.send(p, job.token, report(job), true);
                return;
            }
        if (jobs.size() >= ServerSettings.count("probe.maxConcurrent"))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.probe._probe_service.004"));
        var pos = p.blockPosition();
        var area =
                new SurveyArea(
                        pos.getX(), pos.getY(), pos.getZ(), ServerSettings.count("probe.radius"));
        var job =
                new Job(
                        UUID.randomUUID(),
                        new WeakReference<>(p),
                        area,
                        new NodeSurvey(area),
                        p.serverLevel().getGameTime(),
                        new HashMap<>());
        jobs.addLast(job);
        ProbeNetwork.send(p, job.token, report(job), true);
    }

    private static SurveyReport report(Job job) {
        var state = job.scan.progress();
        List<SurveyReport.Row> rows =
                List.of(
                        new SurveyReport.Row(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_service.005"),
                                state.nodes()
                                        + dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server.probe._probe_service.006")),
                        new SurveyReport.Row(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_service.007"),
                                job.area.radius()
                                        + dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server.probe._probe_service.008")),
                        new SurveyReport.Row(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_service.009"),
                                state.checked()
                                        + " / "
                                        + state.total()
                                        + dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server.probe._probe_service.010")),
                        new SurveyReport.Row(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_service.011"),
                                Integer.toString(state.missing())),
                        new SurveyReport.Row(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_service.012"),
                                Integer.toString(state.failed())));
        String note =
                !state.done()
                        ? dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._probe_service.013")
                        : state.incomplete()
                                ? dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_service.014")
                                : dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_service.015");
        return new SurveyReport(
                1,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.server.probe._probe_service.016"),
                job.area.x() + ", " + job.area.y() + ", " + job.area.z(),
                rows,
                note,
                state.checked(),
                state.total(),
                state.done());
    }

    public static void tick(ServerLevel level) {
        var service = WORLDS.get(level);
        if (service == null) return;
        int budget = ServerSettings.count("probe.chunksPerTick"), size = service.jobs.size();
        for (int i = 0; i < size; i++) {
            Job job = service.jobs.removeFirst();
            var p = job.player.get();
            if (p == null || p.isRemoved() || p.serverLevel() != level || !p.isAlive()) {
                job.scan.stop();
                continue;
            }
            if (level.getGameTime() - job.started >= ServerSettings.count("probe.timeoutTicks"))
                job.scan.stop();
            int used =
                    job.scan.pump(
                            c -> SurveyChunkReader.read(level, job.area, c, job.regions),
                            budget,
                            ServerSettings.count("probe.readAhead"));
            budget -= used;
            var progress = job.scan.progress();
            if (progress.done() || (level.getGameTime() - job.started) % 10 == 0)
                ProbeNetwork.send(p, job.token, report(job), false);
            if (!progress.done()) service.jobs.addLast(job);
        }
        // Next tick starts with a different player, even if the first task consumed the issue
        // budget.
        if (service.jobs.size() > 1) service.jobs.addLast(service.jobs.removeFirst());
    }

    public static void forget(ServerPlayer p) {
        for (var s : WORLDS.values())
            s.jobs.removeIf(
                    j -> {
                        if (j.player.get() == p) {
                            j.scan.stop();
                            return true;
                        }
                        return false;
                    });
    }

    public static void unload(ServerLevel level) {
        var s = WORLDS.remove(level);
        if (s != null) for (var j : s.jobs) j.scan.stop();
    }
}
