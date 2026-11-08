package dev.elementalcircuits.integration;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.block.InscriptionTable;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.network.CircuitNetwork;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.circuit.FixedCircuitAppearance;
import dev.elementalcircuits.server.circuit.WorldCircuits;
import dev.elementalcircuits.server.equipment.WandEquipment;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.workshop.InscriptionAccess;
import dev.elementalcircuits.server.workshop.MaterialInventory;
import dev.elementalcircuits.server.workshop.WorkshopService;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class WorkstationMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h) {
        var p =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        h.getLevel(),
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "station-test"));
        p.getInventory().selected = 0;
        return p;
    }

    private static BlockPos table(GameTestHelper h, ServerPlayer p) {
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        h.getLevel().setBlock(pos, ElementalCircuits.TABLE.get().defaultBlockState(), 3);
        p.moveTo(Vec3.atCenterOf(pos).add(0, 0, -2));
        return pos;
    }

    private static ItemStack wand(ServerPlayer p) {
        var w = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().setItem(0, w);
        return w;
    }

    private static void edit(ServerPlayer p, ItemStack s, int action, String value) {
        CarrierService.forget(p.getUUID());
        CarrierService.request(p, CarrierService.identity(s), 1, action, value);
    }

    private static Blueprint line() {
        var b =
                AssemblyContracts.line(
                        false, Route.Payload.MANA, Rune.Kind.INPUT, Rune.Kind.OUTPUT);
        return new Blueprint(
                b.version(), ServerSettings.circuitRules(), b.runes(), b.routes(), b.continuous());
    }

    @GameTest(template = "empty")
    public static void graphEditingRequiresARealStationSession(GameTestHelper h) {
        var p = player(h);
        RuneTestInventory.stock(p, line());
        var pos = table(h, p);
        var w = wand(p);
        String code = JsonCodecs.blueprint(line());
        edit(p, w, 0, code);
        h.assertTrue(CarrierService.get(w).definition().runes().isEmpty(), "靠近工作台但没有互动仍不能提交刻印");
        InscriptionAccess.interact(p, pos);
        edit(p, w, 0, code);
        h.assertTrue(CarrierService.get(w).definition().equals(line()), "持杖互动后允许刻印");
        p.moveTo(Vec3.atCenterOf(pos).add(9, 0, 0));
        edit(p, w, 0, JsonCodecs.blueprint(Blueprint.empty(RuleResources.defaults())));
        h.assertTrue(CarrierService.get(w).definition().equals(line()), "远离后迟到的刻印请求被拒绝");
        p.moveTo(Vec3.atCenterOf(pos));
        var clone = w.copy();
        p.getInventory().setItem(0, clone);
        h.assertTrue(
                CarrierService.identity(w).equals(CarrierService.identity(clone)),
                "复制品保持相同记录身份用于检验实际持物约束");
        edit(p, clone, 0, JsonCodecs.blueprint(Blueprint.empty(RuleResources.defaults())));
        h.assertTrue(CarrierService.get(clone).definition().equals(line()), "同身份的另一件物品不能继承工作台会话");
        InscriptionAccess.interact(p, pos);
        InscriptionAccess.close(p);
        h.assertTrue(!InscriptionAccess.allowed(p, clone, null), "关闭界面撤销权限");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void platesAreEngravedBeforeThreeMaterialCarriersAreDelivered(GameTestHelper h) {
        var p = player(h);
        RuneTestInventory.stock(p, line());
        var pos = table(h, p);
        long mana = PlayerMana.get(p).available();
        for (var material : Rules.Material.values()) {
            var plate = new ItemStack(MaterialInventory.plate(material));
            p.getInventory().setItem(0, plate);
            InscriptionAccess.interact(p, pos);
            var draft = InscriptionAccess.draft(p);
            h.assertTrue(
                    p.getMainHandItem() == plate
                            && plate.getCount() == 1
                            && p.getInventory().countItem(ElementalCircuits.CIRCUIT_ITEM.get()) == 0
                            && !draft.isEmpty(),
                    "打开刻印不兑换或消耗基板");
            h.assertTrue(
                    CarrierService.get(draft).definition().runes().isEmpty()
                            && CarrierService.get(draft).held() == 0,
                    "草稿不赠送法阵或魔力");
            edit(p, draft, 0, JsonCodecs.blueprint(line().withMaterial(material)));
            var result = p.getMainHandItem();
            h.assertTrue(
                    result.is(ElementalCircuits.CIRCUIT_ITEM.get())
                            && result.getCount() == 1
                            && FixedCircuitAppearance.material(result) == material,
                    "单张基板转成同材质固定法阵：" + material);
            var a = CarrierService.assembly(result);
            h.assertTrue(
                    a.circuit(1).definition().rules().material() == material
                            && a.circuit(1).definition().equals(line().withMaterial(material))
                            && a.circuit(1).held() == 0
                            && !a.powered(),
                    "完成后保留所绘法阵，无赠送魔力或自动起阵");
            h.assertTrue(InscriptionAccess.draft(p).isEmpty(), "交付后草稿会话结束");
            var saved =
                    ItemStack.parseOptional(
                            h.getLevel().registryAccess(),
                            (net.minecraft.nbt.CompoundTag)
                                    result.save(h.getLevel().registryAccess()));
            h.assertTrue(
                    CarrierService.get(saved).definition().equals(line().withMaterial(material)),
                    "物品外观元数据不会丢失刻印修改");
            var target = pos.offset(2, 0, 0);
            h.getLevel()
                    .setBlock(target, ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState(), 3);
            var fixed = (CircuitBlockEntity) h.getLevel().getBlockEntity(target);
            fixed.placed(result, p);
            h.getLevel()
                    .setBlock(
                            target,
                            h.getLevel()
                                    .getBlockState(target)
                                    .setValue(CircuitBlock.MATERIAL, BoardMaterial.WOOD),
                            3);
            fixed.onLoad();
            h.assertTrue(
                    h.getLevel().getBlockState(target).getValue(CircuitBlock.MATERIAL)
                            == BoardMaterial.of(material),
                    "历史块状态从已保存的基板材质恢复");
            var picked =
                    ElementalCircuits.CIRCUIT_BLOCK
                            .get()
                            .getCloneItemStack(
                                    h.getLevel(), target, h.getLevel().getBlockState(target));
            h.assertTrue(
                    FixedCircuitAppearance.material(picked) == material
                            && CarrierService.get(picked).definition().runes().isEmpty()
                            && !CarrierService.identity(picked).equals(fixed.assembly().identity()),
                    "创造中键只复制空白材质，不复制运行身份或配置");
            h.getLevel().setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        }
        h.assertTrue(PlayerMana.get(p).available() == mana, "制作与刻印不补充或消耗玩家魔力");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stackedPlatesAreConsumedOneAtATime(GameTestHelper h) {
        var p = player(h);
        RuneTestInventory.stock(p, line());
        var pos = table(h, p);
        p.getInventory().setItem(0, new ItemStack(ElementalCircuits.BRASS_PLATE.get(), 3));
        InscriptionAccess.interact(p, pos);
        h.assertTrue(
                p.getMainHandItem().getCount() == 3
                        && p.getInventory().countItem(ElementalCircuits.CIRCUIT_ITEM.get()) == 0,
                "互动只进入刻印");
        var draft = InscriptionAccess.draft(p);
        var identity = CarrierService.identity(draft);
        edit(p, draft, 0, JsonCodecs.blueprint(line().withMaterial(Rules.Material.BRASS)));
        h.assertTrue(
                !draft.isEmpty()
                        && CarrierService.identity(draft).equals(identity)
                        && CarrierService.get(draft)
                                .definition()
                                .equals(line().withMaterial(Rules.Material.BRASS)),
                "背包交付不能清空仍要用于确认回复的草稿编码或身份");
        h.assertTrue(
                p.getMainHandItem().getCount() == 2
                        && p.getInventory().countItem(ElementalCircuits.CIRCUIT_ITEM.get()) == 1,
                "完成刻印才消耗一张，成品进入背包");
        edit(p, draft, 0, JsonCodecs.blueprint(line().withMaterial(Rules.Material.BRASS)));
        h.assertTrue(
                p.getMainHandItem().getCount() == 2
                        && p.getInventory().countItem(ElementalCircuits.CIRCUIT_ITEM.get()) == 1,
                "重放旧请求不会再次兑换");
        var b = (WorkstationBlockEntity) h.getLevel().getBlockEntity(pos);
        var tag = b.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(!tag.contains("Carrier") && !tag.contains("Items"), "预览不是复制品库存且不持久化法杖或法阵");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void twoCellDeskPlacesAndBreaksOnceInAllDirections(GameTestHelper h) {
        var p = player(h);
        var base = h.absolutePos(new BlockPos(3, 2, 3));
        var block = ElementalCircuits.WAND_WORKBENCH.get();
        for (int angle : new int[] {0, 90, 180, 270}) {
            p.moveTo(Vec3.atCenterOf(base).add(0, 0, -3));
            p.setYRot(angle);
            var item = new ItemStack(ElementalCircuits.WORKBENCH_ITEM.get(), 2);
            p.setItemInHand(InteractionHand.MAIN_HAND, item);
            h.getLevel().setBlock(base.below(), Blocks.STONE.defaultBlockState(), 3);
            var context =
                    new BlockPlaceContext(
                            p,
                            InteractionHand.MAIN_HAND,
                            item,
                            new BlockHitResult(
                                    Vec3.atCenterOf(base).add(0, -.5, 0),
                                    Direction.UP,
                                    base.below(),
                                    false));
            var result = ((BlockItem) item.getItem()).place(context);
            var state = h.getLevel().getBlockState(base);
            h.assertTrue(
                    result.consumesAction()
                            && WandWorkbenchBlock.complete(h.getLevel(), base)
                            && item.getCount() == 1,
                    "实际放置占两格且只扣一件：" + angle);
            var peer = WandWorkbenchBlock.other(base, state);
            h.assertTrue(
                    h.getLevel().getBlockEntity(base) instanceof WorkstationBlockEntity
                            && h.getLevel().getBlockEntity(peer) == null
                            && WandWorkbenchBlock.master(peer, h.getLevel().getBlockState(peer))
                                    .equals(base),
                    "双格只有一个主实体");
            var area = new AABB(base).inflate(3);
            for (var e : h.getLevel().getEntitiesOfClass(ItemEntity.class, area)) e.discard();
            h.getLevel().destroyBlock(angle % 180 == 0 ? base : peer, true);
            h.assertTrue(
                    h.getLevel().getBlockState(base).isAir()
                            && h.getLevel().getBlockState(peer).isAir(),
                    "拆任意半边移除整张桌子");
            int drops =
                    h.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                            .filter(e -> e.getItem().is(ElementalCircuits.WORKBENCH_ITEM.get()))
                            .mapToInt(e -> e.getItem().getCount())
                            .sum();
            h.assertTrue(drops == 1, "两格只掉落一张桌子：" + drops);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void occupiedPeerRejectsPlacementAndLegacyExpansion(GameTestHelper h) {
        var p = player(h);
        var pos = table(h, p);
        h.getLevel().setBlock(pos, ElementalCircuits.WAND_WORKBENCH.get().defaultBlockState(), 3);
        var peer = WandWorkbenchBlock.other(pos, h.getLevel().getBlockState(pos));
        h.getLevel().setBlock(peer, Blocks.STONE.defaultBlockState(), 3);
        wand(p);
        h.assertTrue(
                !WandWorkbenchBlock.ensureComplete(p, pos)
                        && h.getLevel().getBlockState(peer).is(Blocks.STONE),
                "旧单格台遇到占用不覆盖邻块");
        h.getLevel().setBlock(peer, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(
                WandWorkbenchBlock.ensureComplete(p, pos)
                        && WandWorkbenchBlock.complete(h.getLevel(), pos),
                "主动互动可安全展开旧工作台");
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        p.setYRot(180);
        var item = new ItemStack(ElementalCircuits.WORKBENCH_ITEM.get());
        var state =
                ElementalCircuits.WAND_WORKBENCH
                        .get()
                        .defaultBlockState()
                        .setValue(WandWorkbenchBlock.FACING, Direction.NORTH);
        peer = WandWorkbenchBlock.other(pos, state);
        h.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(peer, Blocks.STONE.defaultBlockState(), 3);
        var context =
                new BlockPlaceContext(
                        p,
                        InteractionHand.MAIN_HAND,
                        item,
                        new BlockHitResult(
                                Vec3.atCenterOf(pos).add(0, -.5, 0),
                                Direction.UP,
                                pos.below(),
                                false));
        h.assertTrue(
                ((BlockItem) item.getItem()).place(context) == InteractionResult.FAIL
                        && item.getCount() == 1
                        && h.getLevel().getBlockState(pos).isAir(),
                "侧面占用时不放半张桌子也不扣物品");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void deskPreviewIsTransientAndIdentityBound(GameTestHelper h) {
        var p = player(h);
        var pos = table(h, p);
        h.getLevel().setBlock(pos, ElementalCircuits.WAND_WORKBENCH.get().defaultBlockState(), 3);
        var w = wand(p);
        WorkshopService.open(p, pos);
        var b = (WorkstationBlockEntity) h.getLevel().getBlockEntity(pos);
        h.assertTrue(
                b.parts().equals(WandEquipment.assembly(w)) && p.getMainHandItem() == w,
                "桌面只投影正在修改的法杖，实际物品仍在手中");
        var tag = b.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(
                tag.getAllKeys().equals(Set.of("Head", "Body", "Tail")), "同步仅包含渲染需要的三部件，未泄漏完整法阵状态");
        var remote = new WorkstationBlockEntity(pos, h.getLevel().getBlockState(pos));
        remote.onDataPacket(null, b.getUpdatePacket(), h.getLevel().registryAccess());
        h.assertTrue(remote.parts() != null, "实时更新包进入客户端预览解码");
        WorkshopService.request(p, CarrierService.identity(w), pos.asLong(), 3, "");
        remote.onDataPacket(null, b.getUpdatePacket(), h.getLevel().registryAccess());
        h.assertTrue(remote.parts() == null, "空更新包也必须清除旧法杖");
        h.assertTrue(b.parts() == null && p.getMainHandItem() == w, "关闭后桌面空置且不产生第二根法杖");
        b.show(p, w);
        b.onLoad();
        h.assertTrue(b.parts() == null, "加载时不恢复幽灵预览");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void placedCircuitEditingMustGoThroughInscriptionTable(GameTestHelper h) {
        var p = player(h);
        RuneTestInventory.stock(p, line());
        var pos = table(h, p);
        var w = wand(p);
        var target = pos.offset(2, 0, 0);
        h.getLevel().setBlock(target, ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState(), 3);
        var fixed = (CircuitBlockEntity) h.getLevel().getBlockEntity(target);
        fixed.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), p);
        WorldCircuits.register(fixed);
        edit(p, fixed.carrier(), 0, JsonCodecs.blueprint(line()));
        h.assertTrue(
                fixed.assembly().circuit(1).definition().runes().isEmpty(), "直接针对世界固定法阵发刻印请求被拒绝");
        InscriptionAccess.select(p, fixed);
        InscriptionAccess.interact(p, pos);
        edit(p, fixed.carrier(), 0, JsonCodecs.blueprint(line()));
        h.assertTrue(
                fixed.assembly().circuit(1).definition().equals(line()) && p.getMainHandItem() == w,
                "法杖选定后经附近刻阵台编辑固定法阵");
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(!InscriptionAccess.allowed(p, fixed.carrier(), fixed), "工作台消失立即撤销固定法阵编辑权限");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void collisionMatchesTheSlopeAndProtectsCrystal(GameTestHelper h) {
        var l = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        for (var direction : Direction.Plane.HORIZONTAL) {
            var altar =
                    ElementalCircuits.TABLE
                            .get()
                            .defaultBlockState()
                            .setValue(InscriptionTable.FACING, direction);
            h.assertTrue(
                    altar.getShape(l, pos)
                            .toAabbs()
                            .equals(net.minecraft.world.phys.shapes.Shapes.block().toAabbs()),
                    "刻阵台只显示占用方块边界");
            var crystal = altar.getCollisionShape(l, pos);
            var hit = crystal.clip(new Vec3(.5, 1.4, .5), new Vec3(.5, .65, .5), BlockPos.ZERO);
            h.assertTrue(
                    hit != null && hit.getLocation().y > .98 && crystal.max(Direction.Axis.Y) <= 1,
                    "水晶纳入碰撞且不伸入上方格子：" + direction);
            for (var part : BedPart.values()) {
                var state =
                        ElementalCircuits.WAND_WORKBENCH
                                .get()
                                .defaultBlockState()
                                .setValue(WandWorkbenchBlock.FACING, direction)
                                .setValue(WandWorkbenchBlock.PART, part);
                h.assertTrue(
                        state.getShape(l, pos)
                                .toAabbs()
                                .equals(net.minecraft.world.phys.shapes.Shapes.block().toAabbs()),
                        "两格桌子每半边只显示占用方块边界");
                var shape = state.getCollisionShape(l, pos);
                double[] tops = new double[2];
                int n = 0;
                for (double z : new double[] {.2, .8}) {
                    double x = .8, tz = z;
                    int turns =
                            switch (direction) {
                                case EAST -> 1;
                                case SOUTH -> 2;
                                case WEST -> 3;
                                default -> 0;
                            };
                    for (int i = 0; i < turns; i++) {
                        double nx = 1 - tz;
                        tz = x;
                        x = nx;
                    }
                    var falling = new AABB(x - .005, 1.4, tz - .005, x + .005, 2.4, tz + .005);
                    tops[n++] = 1.4 + shape.collide(Direction.Axis.Y, falling, -1.4);
                    double expected =
                            WorkstationGeometry.desk().surfaceY()
                                    + (.5 - z) * Math.tan(Math.toRadians(12));
                    h.assertTrue(
                            Math.abs(tops[n - 1] - expected) < .012,
                            "实际碰撞跟随桌面斜坡而非隐藏整块：" + direction + "/" + part + "/" + tops[n - 1]);
                }
                h.assertTrue(
                        tops[0] - tops[1] > .1 && shape.max(Direction.Axis.Y) <= 1,
                        "两半桌面的坡度与占位边界正确");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unfinishedOrInvalidEngravingsNeverConsumePlates(GameTestHelper h) {
        var p = player(h);
        var pos = table(h, p);
        var plate = new ItemStack(ElementalCircuits.WOOD_PLATE.get(), 2);
        p.getInventory().setItem(0, plate);
        InscriptionAccess.interact(p, pos);
        var draft = InscriptionAccess.draft(p);
        edit(p, draft, 0, JsonCodecs.blueprint(Blueprint.empty(RuleResources.defaults())));
        edit(p, draft, 0, "{broken");
        edit(p, draft, 0, JsonCodecs.blueprint(line().withMaterial(Rules.Material.CRYSTAL)));
        h.assertTrue(
                plate.getCount() == 2
                        && p.getInventory().countItem(ElementalCircuits.CIRCUIT_ITEM.get()) == 0
                        && CarrierService.get(draft).definition().runes().isEmpty(),
                "空白、损坏或伪造材质均不扣板不交付");
        edit(p, draft, 1, "");
        edit(p, draft, 9, "WOOD");
        edit(p, draft, 4, "BRASS");
        h.assertTrue(
                !CarrierService.get(draft).powered()
                        && CarrierService.assembly(draft).slots().size() == 1
                        && plate.getCount() == 2,
                "草稿不能施法、添加槽位或更换基板");
        edit(p, draft, 14, "");
        edit(p, draft, 0, JsonCodecs.blueprint(line()));
        h.assertTrue(
                InscriptionAccess.draft(p).isEmpty()
                        && plate.getCount() == 2
                        && p.getInventory().countItem(ElementalCircuits.CIRCUIT_ITEM.get()) == 0
                        && h.getLevel().getBlockState(pos).getValue(InscriptionTable.BOARD)
                                == BoardMaterial.EMPTY,
                "取消后保留基板并清理预览，旧请求失效");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void movedPlatesAndExpiredSessionsCannotFinishEngraving(GameTestHelper h) {
        var p = player(h);
        var pos = table(h, p);
        var plate = new ItemStack(ElementalCircuits.WOOD_PLATE.get(), 2);
        p.getInventory().setItem(0, plate);
        InscriptionAccess.interact(p, pos);
        var old = InscriptionAccess.draft(p);
        p.getInventory().setItem(0, plate.copy());
        edit(p, old, 0, JsonCodecs.blueprint(line()));
        h.assertTrue(
                p.getMainHandItem().getCount() == 2 && InscriptionAccess.draft(p).isEmpty(),
                "即使同材质也不能用替换的物理基板完成旧草稿");
        InscriptionAccess.interact(p, pos);
        var next = InscriptionAccess.draft(p);
        edit(p, old, 14, "");
        h.assertTrue(InscriptionAccess.draft(p) == next, "迟到的关闭请求不能取消新的刻印会话");
        edit(p, old, 0, JsonCodecs.blueprint(line()));
        h.assertTrue(
                !CarrierService.identity(old).equals(CarrierService.identity(next))
                        && p.getMainHandItem().getCount() == 2,
                "重新进入产生独立草稿，旧请求不能指向新板");
        p.moveTo(Vec3.atCenterOf(pos).add(10, 0, 0));
        InscriptionAccess.tick(p);
        p.moveTo(Vec3.atCenterOf(pos));
        edit(p, next, 0, JsonCodecs.blueprint(line()));
        h.assertTrue(
                InscriptionAccess.draft(p).isEmpty() && p.getMainHandItem().getCount() == 2,
                "离开再回来也不能恢复失效草稿");
        InscriptionAccess.interact(p, pos);
        next = InscriptionAccess.draft(p);
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        edit(p, next, 0, JsonCodecs.blueprint(line()));
        h.assertTrue(
                p.getInventory().countItem(ElementalCircuits.CIRCUIT_ITEM.get()) == 0
                        && p.getMainHandItem().getCount() == 2,
                "工作台拆除不交付成品");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryDropsOnlyOneCompletedArray(GameTestHelper h) {
        var p = player(h);
        var pos = table(h, p);
        for (int i = 0; i < 36; i++)
            p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        var plate = new ItemStack(ElementalCircuits.CRYSTAL_PLATE.get(), 2);
        p.getInventory().setItem(0, plate);
        RuneTestInventory.stock(p, line());
        InscriptionAccess.interact(p, pos);
        var draft = InscriptionAccess.draft(p);
        var id = CarrierService.identity(draft);
        edit(p, draft, 0, JsonCodecs.blueprint(line().withMaterial(Rules.Material.CRYSTAL)));
        var drops =
                h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5)).stream()
                        .filter(
                                e ->
                                        e.getItem().is(ElementalCircuits.CIRCUIT_ITEM.get())
                                                && CarrierService.identity(e.getItem()).equals(id))
                        .toList();
        h.assertTrue(
                plate.getCount() == 1
                        && drops.size() == 1
                        && CarrierService.get(drops.get(0).getItem())
                                .definition()
                                .equals(line().withMaterial(Rules.Material.CRYSTAL)),
                "背包满时完成的法阵只掉落一次且保留刻印");
        edit(p, draft, 0, JsonCodecs.blueprint(line().withMaterial(Rules.Material.CRYSTAL)));
        h.assertTrue(plate.getCount() == 1, "重复保存不会继续消耗基板");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void engravingSessionBelongsToItsPlayer(GameTestHelper h) {
        var p = player(h);
        var pos = table(h, p);
        p.getInventory().setItem(0, new ItemStack(ElementalCircuits.WOOD_PLATE.get(), 2));
        InscriptionAccess.interact(p, pos);
        var draft = InscriptionAccess.draft(p);
        var other = player(h);
        other.moveTo(p.position());
        other.getInventory().setItem(0, new ItemStack(ElementalCircuits.WOOD_PLATE.get(), 2));
        edit(other, draft, 0, JsonCodecs.blueprint(line()));
        h.assertTrue(
                p.getMainHandItem().getCount() == 2
                        && other.getMainHandItem().getCount() == 2
                        && CarrierService.get(draft).definition().runes().isEmpty(),
                "另一玩家不能通过草稿编号消费或获得物品");
        InscriptionAccess.interact(other, pos);
        InscriptionAccess.close(p);
        h.assertTrue(
                h.getLevel().getBlockState(pos).getValue(InscriptionTable.BOARD)
                        == BoardMaterial.WOOD,
                "先前玩家关闭不会清掉当前玩家的桌面预览");
        InscriptionAccess.close(other);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void draftStateSurvivesEditorTransport(GameTestHelper h) {
        var packet =
                new CircuitNetwork.Editor(
                        UUID.randomUUID(),
                        JsonCodecs.blueprint(line()),
                        false,
                        0,
                        true,
                        "完成刻印",
                        WandResources.definitions().basic(),
                        0,
                        1,
                        true,
                        "",
                        "",
                        false,
                        UUID.randomUUID(),
                        true);
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            CircuitNetwork.writeEditor(packet, buf);
            h.assertTrue(
                    CircuitNetwork.readEditor(buf).equals(packet) && buf.readableBytes() == 0,
                    "草稿标志、请求令牌和法阵完整往返");
        } finally {
            buf.release();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fixedArraysHaveLowProfilesInAllMaterials(GameTestHelper h) {
        for (var material :
                new BoardMaterial[] {
                    BoardMaterial.WOOD, BoardMaterial.BRASS, BoardMaterial.CRYSTAL
                })
            for (var direction : Direction.Plane.HORIZONTAL) {
                var state =
                        ElementalCircuits.CIRCUIT_BLOCK
                                .get()
                                .defaultBlockState()
                                .setValue(CircuitBlock.MATERIAL, material)
                                .setValue(CircuitBlock.FACING, direction);
                var shape = state.getCollisionShape(h.getLevel(), h.absolutePos(BlockPos.ZERO));
                h.assertTrue(
                        shape.max(Direction.Axis.Y) < .23
                                && shape.min(Direction.Axis.Y) == 0
                                && !state.canOcclude(),
                        "低矮阵盘不会产生旧整立方碰撞或遮挡：" + material + "/" + direction);
            }
        h.succeed();
    }
}
