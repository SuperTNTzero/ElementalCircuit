package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.application.NodeSurvey;
import dev.elementalcircuits.application.observation.SurveyReport;
import dev.elementalcircuits.domain.survey.SurveyArea;

import java.util.*;
import java.util.concurrent.*;

public final class ProbeContracts {
    private static int checks;

    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }

    private static void rejects(Runnable task) {
        boolean bad = false;
        try {
            task.run();
        } catch (RuntimeException e) {
            bad = true;
        }
        check(bad, "非法探测参数拒绝");
    }

    private static void area() {
        var a = new SurveyArea(0, 64, 0, 500);
        check(
                a.contains(500, 64, 0)
                        && a.contains(0, -436, 0)
                        && !a.contains(500, 65, 0)
                        && !a.contains(300, 64, 401),
                "三维球体包含精确边缘，不能当成立方体或圆柱");
        var chunks = a.chunks();
        check(
                chunks.size() > 3000
                        && chunks.size() < 4096
                        && new HashSet<>(chunks).size() == chunks.size(),
                "500格候选为数千区块，去重并跳过方角");
        Random r = new Random(183);
        for (int i = 0; i < 400; i++) {
            int x = r.nextInt(1001) - 500, z = r.nextInt(1001) - 500;
            if (a.contains(x, 64, z))
                check(
                        chunks.contains(
                                new SurveyArea.Chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))),
                        "负坐标与边界位置都有候选区块");
        }
        var negative = new SurveyArea(-16, -60, -17, 1);
        check(
                negative.chunks().contains(new SurveyArea.Chunk(-2, -2))
                        && negative.contains(-16, -61, -17),
                "负整数区块边界不截断");
        check(!new SurveyArea(30000000, 0, 30000000, 2048).chunks().isEmpty(), "世界边界裁剪没有算术回绕");
        rejects(() -> new SurveyArea(Integer.MIN_VALUE, 0, 0, 1));
        rejects(() -> new SurveyArea(0, 0, 0, 0));
        rejects(() -> new SurveyArea(0, 0, 0, 2049));
    }

    private static void async() {
        var scan = new NodeSurvey(new SurveyArea(0, 0, 0, 20));
        List<CompletableFuture<NodeSurvey.Sample>> futures = new ArrayList<>();
        NodeSurvey.Reader reader =
                c -> {
                    var f = new CompletableFuture<NodeSurvey.Sample>();
                    futures.add(f);
                    return f;
                };
        check(
                scan.pump(reader, 16, 2) == 2
                        && scan.progress().pending() == 2
                        && scan.progress().checked() == 0,
                "挂起I/O不阻塞，读取数受上限约束");
        check(scan.pump(reader, 16, 2) == 0 && futures.size() == 2, "未完成读请求不重复发出");
        futures.get(0).complete(new NodeSurvey.Sample(true, 2));
        futures.get(1).complete(new NodeSurvey.Sample(false, 0));
        check(
                scan.pump(reader, 1, 2) == 1
                        && scan.progress().checked() == 2
                        && scan.progress().nodes() == 2
                        && scan.progress().missing() == 1,
                "部分完成后精确累计，只发出配置预算");
        futures.get(2).completeExceptionally(new IllegalArgumentException());
        scan.pump(reader, 0, 2);
        check(
                scan.progress().failed() == 1 && scan.progress().checked() == 3,
                "读取错误单独记录，不能当成已生成零节点");
        int total = scan.progress().total();
        while (!scan.progress().done())
            scan.pump(c -> CompletableFuture.completedFuture(new NodeSurvey.Sample(true, 1)), 3, 2);
        check(
                scan.progress().checked() == total
                        && scan.progress().generated() == total - 2
                        && scan.progress().nodes() == total - 1
                        && scan.progress().incomplete(),
                "完整检查后保留未生成及错误覆盖范围");
        check(scan.pump(reader, 100, 2) == 0, "完成任务不能重复累计");
        var cancelled = new NodeSurvey(new SurveyArea(0, 0, 0, 500));
        cancelled.pump(reader, 1, 1);
        var nativeFuture = futures.getLast();
        cancelled.stop();
        check(
                cancelled.progress().done()
                        && cancelled.progress().incomplete()
                        && !nativeFuture.isCancelled()
                        && cancelled.pump(reader, 16, 2) == 0,
                "取消只释放观察引用，不取消引擎共享读取或重启");
        var thrown = new NodeSurvey(new SurveyArea(0, 0, 0, 1));
        thrown.pump(
                c -> {
                    throw new IllegalStateException();
                },
                1,
                1);
        check(thrown.progress().failed() == 1, "同步拒绝也进入失败统计");
        rejects(() -> scan.pump(reader, -1, 2));
        rejects(() -> scan.pump(reader, 1, 17));
        rejects(() -> new NodeSurvey.Sample(false, 1));
    }

    private static void report() {
        var rows = new ArrayList<SurveyReport.Row>();
        rows.add(new SurveyReport.Row("天然节点", "3 个"));
        var r = new SurveyReport(1, "勘测", "0,64,0", rows, "部分区域尚未生成", 2, 10, false);
        rows.clear();
        check(
                r.rows().size() == 1 && JsonCodecs.readSurvey(JsonCodecs.survey(r)).equals(r),
                "报告脱离可变容器并严格编解码");
        rejects(
                () ->
                        JsonCodecs.readSurvey(
                                JsonCodecs.survey(r).replace("\"checked\":2", "\"checked\":2.5")));
        rejects(() -> new SurveyReport(1, "x", "y", List.of(), "", 3, 2, false));
        rejects(() -> new SurveyReport.Row("x", "a".repeat(257)));
        rejects(() -> JsonCodecs.readSurvey("{}"));
        rejects(
                () ->
                        JsonCodecs.survey(
                                new SurveyReport(
                                        1,
                                        "x",
                                        "y",
                                        java.util.Collections.nCopies(
                                                32,
                                                new SurveyReport.Row(
                                                        "场".repeat(64), "强".repeat(256))),
                                        "",
                                        0,
                                        0,
                                        true)));
    }

    public static int run() {
        area();
        async();
        report();
        return checks;
    }
}
