package dev.elementalcircuits.integration;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.entity.ManaSlime;
import dev.elementalcircuits.server.ecology.ManaEcology;
import dev.elementalcircuits.server.mana.*;
import dev.elementalcircuits.server.probe.ProbeReports;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

import java.util.UUID;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ManaEcologyMinecraftContracts {
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h) {
        var p =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        h.getLevel(),
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ecology-test"));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return p;
    }

    @GameTest(template = "empty")
    public static void gelAndInstantPotionsUseNativeBrewing(GameTestHelper h) {
        var p = player(h);
        var pool = PlayerMana.get(p);
        pool.setAmount(0);
        var gel = new ItemStack(ElementalCircuits.MANA_GEL.get(), 2);
        gel.getItem().finishUsingItem(gel, h.getLevel(), p);
        h.assertTrue(
                pool.available() == ServerSettings.mana("ecology.gelRestore")
                        && gel.getCount() == 1,
                "食用凝胶恢复魔力并消耗一个");
        var brew = h.getLevel().potionBrewing();
        var potion =
                brew.mix(
                        new ItemStack(ElementalCircuits.MANA_GEL.get()),
                        PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
        var glass = potion.getItem().finishUsingItem(potion, h.getLevel(), p);
        h.assertTrue(
                glass.is(Items.GLASS_BOTTLE)
                        && pool.available() == 48 * Fixed.ONE
                        && !p.hasEffect(ManaBrewing.RECOVERY),
                "复原药水瞬时生效，非旧回复buff");
        var strong =
                brew.mix(
                        new ItemStack(Items.GLOWSTONE_DUST),
                        PotionContents.createItemStack(Items.POTION, ManaBrewing.INSTANT_MANA));
        strong.getItem().finishUsingItem(strong, h.getLevel(), p);
        h.assertTrue(pool.available() == pool.capacity(), "强效瞬间恢复截断至容量");
        pool.setAmount(0);
        pool.debit(0);
        ManaBrewing.RESTORATION.get().applyInstantenousEffect(null, null, p, 0, .25);
        h.assertTrue(pool.available() == 8 * Fixed.ONE, "喷溅衰减按距离比例计算");
        PlayerMana.save(p);
        PlayerMana.forget(p);
        h.assertTrue(PlayerMana.get(p).available() == 8 * Fixed.ONE, "瞬时恢复可保存，不重复执行");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void conversionRequiresContinuousFieldAndKeepsSize(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 1, 2));
        Slime slime = EntityType.SLIME.create(level);
        slime.setPos(Vec3.atCenterOf(pos));
        slime.setSize(4, true);
        slime.setHealth(7);
        slime.setCustomName(net.minecraft.network.chat.Component.literal("test-slime"));
        level.addFreshEntity(slime);
        var field = WorldManaField.get(level);
        var cell = field.discover(level, pos);
        field.field().inject(cell, 64 * Fixed.ONE);
        h.assertTrue(ManaEcology.expose(slime, level) == null, "强场不能立即转化");
        var disk = slime.saveWithoutId(new CompoundTag());
        var restoredSlime = EntityType.SLIME.create(level);
        restoredSlime.load(disk);
        h.assertTrue(
                restoredSlime.getPersistentData().getInt("elementalcircuits_mana_exposure") == 20,
                "原生实体保存与加载保留转化进度");
        field.field().debit(cell, field.field().amount(cell));
        ManaEcology.expose(slime, level);
        field.field().inject(cell, 64 * Fixed.ONE);
        ManaSlime converted = null;
        int count = (ServerSettings.count("ecology.conversionTicks") + 19) / 20;
        for (int i = 0; i < count; i++) {
            converted = ManaEcology.expose(slime, level);
            if (i < count - 1) h.assertTrue(converted == null, "离开强场后重新累计");
        }
        h.assertTrue(
                converted != null
                        && converted.getSize() == 4
                        && converted.getHealth() == 7
                        && converted.hasCustomName()
                        && slime.isRemoved(),
                "转化保留尺寸血量名称，仅替换一个实体");
        h.assertTrue(ManaEcology.expose(converted, level) == null, "魔力史莱姆不会再次转化");
        converted.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aerialNodePersistsAndComplementsGroundSource(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(pos, ElementalCircuits.AERIAL_MANA_NODE.get().defaultBlockState());
        var node = (ManaNodeBlockEntity) level.getBlockEntity(pos);
        h.assertTrue(
                node.natural()
                        && node.aerial()
                        && node.capacity() == 1024 * Fixed.ONE
                        && node.remaining() == node.capacity(),
                "浮光是独立天然节点，初始储量1024");
        h.assertTrue(node.emission() == Fixed.ONE, "浮光节点默认每场步输出1");
        node.emitted(Fixed.ONE);
        long before = node.remaining();
        node.regenerate(2 * Fixed.ONE, 20);
        h.assertTrue(node.remaining() == node.capacity(), "天然补能封顶");
        node.emitted(Fixed.ONE);
        var saved = node.saveWithoutMetadata(level.registryAccess());
        var restored = new ManaNodeBlockEntity(pos, node.getBlockState());
        restored.setLevel(level);
        restored.loadWithComponents(saved, level.registryAccess());
        h.assertTrue(
                restored.remaining() == before && restored.capacity() == node.capacity(),
                "重新加载不补满且保留冻结储量");
        WorldManaField.get(level).register(node);
        h.assertTrue(ManaEcology.canSpawn(level, pos), "天然浮光节点附近允许生态生成");
        h.assertTrue(ProbeReports.block(level, pos).title().equals("浮光节点"), "探测区分浮光与地脉");
        h.assertTrue(node.getBlockState().getCollisionShape(level, pos).isEmpty(), "光团无实体碰撞");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void splitRetainsManaSlimeType(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 1, 2));
        var slime = ManaEcology.SLIME.get().create(level);
        slime.setPos(Vec3.atCenterOf(pos));
        slime.setSize(2, true);
        level.addFreshEntity(slime);
        slime.setHealth(0);
        slime.remove(Entity.RemovalReason.KILLED);
        var children = level.getEntitiesOfClass(ManaSlime.class, new AABB(pos).inflate(2));
        h.assertTrue(
                children.size() >= 2 && children.stream().allMatch(s -> s.getSize() == 1),
                "原生分裂保留魔力史莱姆类型");
        for (var child : children) child.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void configuredAerialFeaturePlacesRealNode(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 3, 2));
        var id =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        ElementalCircuits.ID, "aerial_mana_node");
        var feature =
                level.registryAccess()
                        .registryOrThrow(
                                net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                        .get(id);
        h.assertTrue(
                feature != null
                        && feature.place(
                                level,
                                level.getChunkSource().getGenerator(),
                                net.minecraft.util.RandomSource.create(45),
                                pos),
                "原生世界生成配置可放置光团节点");
        h.assertTrue(
                level.getBlockEntity(pos) instanceof ManaNodeBlockEntity n
                        && n.aerial()
                        && n.natural(),
                "生成结果接入天然供能和存档接口");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void smallManaSlimeDropsOnlyManaGel(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 1, 2));
        for (int i = 0; i < 20; i++) {
            var slime = ManaEcology.SLIME.get().create(level);
            slime.setPos(Vec3.atCenterOf(pos));
            slime.setSize(1, true);
            level.addFreshEntity(slime);
            slime.hurt(level.damageSources().generic(), 100);
            slime.discard();
        }
        var drops =
                level.getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(3));
        h.assertTrue(
                !drops.isEmpty()
                        && drops.stream()
                                .allMatch(e -> e.getItem().is(ElementalCircuits.MANA_GEL.get())),
                "真实死亡掉落魔力凝胶，不掉普通粘液球");
        for (var drop : drops) drop.discard();
        h.succeed();
    }
}
