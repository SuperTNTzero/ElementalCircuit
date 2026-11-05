package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.AssemblyResources;
import dev.elementalcircuits.adapter.resources.LogicResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.client.editor.BookLayout;
import dev.elementalcircuits.domain.book.BlueprintVolume;
import dev.elementalcircuits.domain.book.BookSelection;
import dev.elementalcircuits.domain.book.NotebookInspection;
import dev.elementalcircuits.domain.book.SpellbookInspection;
import dev.elementalcircuits.domain.book.WorkshopInspection;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.examples.LogicExamples;
import dev.elementalcircuits.domain.mana.ManaPool;

import java.util.*;

public final class BookContracts {
    private static int count;

    private static void check(boolean value, String why) {
        count++;
        if (!value) throw new AssertionError(why);
    }

    private static void reject(Runnable r) {
        boolean denied = false;
        try {
            r.run();
        } catch (RuntimeException e) {
            denied = true;
        }
        check(denied, "非法书页必须拒绝");
    }

    public static int run() {
        var blank = BlueprintVolume.empty(new UUID(29, 10));
        var emptyView = NotebookInspection.inspect(blank, "空白");
        check(
                JsonCodecs.readNotebook(JsonCodecs.notebook(emptyView)).equals(emptyView),
                "空白蓝图簿无当前蓝图也可往返");
        var bp = LogicExamples.alternating(RuleResources.defaults(), LogicResources.defaults());
        var one = blank.add("火水切换", bp);
        var two = one.add("同一配置", bp);
        check(
                blank.pages().isEmpty() && one.pages().size() == 1 && two.selected() == 2,
                "修改生成独立不可变卷册");
        reject(() -> one.pages().clear());
        var removal = two.remove(1).add("第三页", bp);
        check(removal.pages().get(1).id() == 3 && removal.nextId() == 4, "删除不复用稳定页编号");
        check(removal.rename(2, "改名").page(2).blueprint().equals(bp), "改名保留配置");
        check(one.remove(1).selected() == 0, "最后一页删除成为空卷");
        for (String title : List.of("", " ", "a".repeat(33), "坏\n名", "§c格式"))
            reject(() -> blank.add(title, bp));
        reject(
                () ->
                        new BlueprintVolume(
                                1, blank.identity(), 3, 1, List.of(one.page(1), one.page(1))));
        reject(() -> new BlueprintVolume(2, blank.identity(), 1, 0, List.of()));
        reject(() -> two.select(9));
        var full = blank;
        for (int i = 0; i < 16; i++) full = full.add("页" + i, bp);
        var cap = full;
        reject(() -> cap.add("超量", bp));
        check(JsonCodecs.readVolume(JsonCodecs.volume(cap)).equals(cap), "十六页完整持久化");
        var inspection = NotebookInspection.inspect(cap, "预览");
        check(
                JsonCodecs.readNotebook(JsonCodecs.notebook(inspection)).equals(inspection),
                "观察只携带选页配置和目录");
        String text = JsonCodecs.volume(one);
        reject(() -> JsonCodecs.readVolume(text.replace("\"id\":1", "\"id\":1.1")));
        reject(() -> JsonCodecs.readVolume(text.replace("\"selected\":1", "\"selected\":2")));
        reject(() -> JsonCodecs.readVolume("[".repeat(40) + "]".repeat(40)));
        reject(() -> JsonCodecs.readVolume(" ".repeat(JsonCodecs.VOLUME_LIMIT + 1)));
        check(
                !text.contains("supplied") && !text.contains("held") && !text.contains("inventory"),
                "蓝图簿不能保存或补充运行资源");
        check(
                BlueprintTextCode.decode(BlueprintTextCode.encode(one.page(1).blueprint()))
                        .equals(bp),
                "蓝图页与现有文本码相容");
        var a =
                new CircuitAssembly(
                        new UUID(29, 11), AssemblyResources.rules(), new TickCircuit(bp));
        a.add(RuleResources.defaults());
        a.select(1);
        a.circuit(1).start();
        var mana = new ManaPool(64 * Fixed.ONE, 0, 0);
        for (int i = 0; i < 13; i++) a.tick(mana, 1);
        var runtime = JsonCodecs.snapshot(a.circuit(1));
        var other = JsonCodecs.snapshot(a.circuit(2));
        BookSelection.cycle(a, 1);
        check(a.selected() == 2 && a.circuit(1).powered(), "切换只选择，不破坏其他法阵的运行状态");
        BookSelection.cycle(a, 1);
        check(a.selected() == 1, "循环下一页回首");
        BookSelection.cycle(a, -1);
        check(a.selected() == 2, "循环上一页回尾");
        check(
                runtime.equals(JsonCodecs.snapshot(a.circuit(1)))
                        && other.equals(JsonCodecs.snapshot(a.circuit(2))),
                "切页不推进或清空计数器、缓存与账本");
        reject(() -> BookSelection.cycle(a, 0));
        reject(() -> BookSelection.cycle(a, Integer.MAX_VALUE));
        String saved = JsonCodecs.assembly(a);
        check(JsonCodecs.assembly(JsonCodecs.readAssembly(saved)).equals(saved), "法术书保存完整程序集现场");
        var view = SpellbookInspection.inspect(a, "本书");
        check(JsonCodecs.readSpellbook(JsonCodecs.spellbook(view)).equals(view), "法术书观察含多页配置");
        a.select(1);
        check(view.selected() == 2, "观察不跟随可变模型修改");
        reject(
                () ->
                        new SpellbookInspection(
                                a.identity(),
                                1,
                                List.of(view.slots().get(0), view.slots().get(0)),
                                "重复"));
        var defs = WandResources.definitions();
        var stock = new HashMap<String, Integer>();
        stock.put(defs.parts().get(0).id(), 2);
        var workshop =
                new WorkshopInspection(a.identity(), 22, defs.basic(), 9, stock, 2, "工坊", true);
        stock.clear();
        check(
                workshop.stock().size() == 1
                        && JsonCodecs.readWorkshop(JsonCodecs.workshop(workshop)).equals(workshop),
                "工作台快照独立于库存且完整往返");
        for (int w : new int[] {320, 427, 640, 960, 1280})
            for (int h : new int[] {240, 300, 400, 720})
                for (int radius : new int[] {4, 6, 8}) {
                    var l = BookLayout.of(w, h);
                    check(
                            l.previewX() >= l.left() + l.list()
                                    && l.previewX() + l.previewWidth() <= w - l.left(),
                            "预览和目录不重叠");
                    for (int q = -radius; q <= radius; q++)
                        for (int r = -radius; r <= radius; r++) {
                            var hex = new Hex(q, r);
                            if (!hex.inside(radius)) continue;
                            double x = l.x(hex, radius), y = l.y(hex, radius), s = l.size(radius);
                            check(
                                    x - s >= l.previewX()
                                            && x + s <= l.previewX() + l.previewWidth()
                                            && y - s >= l.previewY()
                                            && y + s <= l.previewY() + l.previewHeight(),
                                    "全格盘预览边界");
                        }
                }
        return count;
    }
}
