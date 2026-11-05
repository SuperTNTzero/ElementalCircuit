package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.application.observation.SpellVisual;
import dev.elementalcircuits.application.observation.VisualBudget;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.combat.DamageAffinity;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.spell.CombatEffects;
import dev.elementalcircuits.server.spell.ElementalCombatAdapter;
import dev.elementalcircuits.server.spell.ElementalDamageSource;
import dev.elementalcircuits.server.spell.SpellDamage;
import dev.elementalcircuits.server.spell.SpellEffects;
import dev.elementalcircuits.server.spell.SpellVisuals;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.*;

import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ElementalDamageMinecraftContracts {
    private static Mixture mana(Element e, boolean inverse, long amount) {
        long[] v = new long[17];
        v[e.component(inverse)] = amount;
        return new Mixture(v);
    }

    private static Mixture mana(Element e) {
        return mana(e, false, Fixed.ONE);
    }

    private static LivingEntity pig(GameTestHelper h) {
        var e = h.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        e.setNoAi(true);
        e.setNoGravity(true);
        return e;
    }

    private static void hit(GameTestHelper h, LivingEntity e, Mixture mana) {
        ElementalCombatAdapter.apply(h.getLevel(), null, e, mana, new Vec3(0, 0, 1), Fixed.ONE);
    }

    private static void reset(LivingEntity e) {
        e.invulnerableTime = 0;
    }

    @GameTest(template = "empty")
    public static void inverseArcaneCancelsBeforeNativeEffects(GameTestHelper h) {
        var anti = mana(Element.ARCANE, true, Fixed.ONE);
        for (Element element : Element.values()) {
            if (element == Element.PURE) continue;
            for (boolean inverse : new boolean[] {false, true}) {
                if (element == Element.ARCANE && inverse) continue;
                var entity = pig(h);
                float before = entity.getHealth();
                hit(h, entity, mana(element, inverse, Fixed.ONE).plus(anti));
                h.assertTrue(
                        entity.getHealth() == before
                                && !entity.isOnFire()
                                && !entity.hasEffect(CombatEffects.DRENCHED)
                                && entity.getTicksFrozen() == 0
                                && entity.invulnerableTime == 0,
                        "元素被反相奥术抵消后不调用原生伤害或写入状态：" + element + inverse);
                entity.discard();
            }
        }
        var burning = pig(h);
        burning.setRemainingFireTicks(80);
        hit(h, burning, mana(Element.FIRE).plus(anti));
        h.assertTrue(burning.getRemainingFireTicks() == 80, "不撤销前一发法术留下的世界状态");
        var survivor = pig(h);
        float hp = survivor.getHealth();
        hit(h, survivor, mana(Element.FIRE, false, 2 * Fixed.ONE).plus(anti));
        h.assertTrue(survivor.getHealth() == hp - 2 && survivor.isOnFire(), "只让未被抵消的一份火实际造成伤害");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void queuedSameContactCancellation(GameTestHelper h) {
        var e = pig(h);
        float health = e.getHealth();
        var fire = mana(Element.FIRE);
        var water = mana(Element.WATER);
        hit(h, e, fire.plus(water));
        h.assertTrue(
                e.getHealth() == health
                        && !e.isOnFire()
                        && !e.hasEffect(CombatEffects.DRENCHED)
                        && e.invulnerableTime == 0,
                "同量火水先反应，不造成伤害或写入状态");
        hit(h, e, fire.plus(fire).plus(water));
        h.assertTrue(
                e.getHealth() == health - 2 && e.isOnFire() && !e.hasEffect(CombatEffects.DRENCHED),
                "相消后只剩一份火生效");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void queuedSurvivorOrder(GameTestHelper h) {
        var a = pig(h);
        var b = pig(h);
        float before = a.getHealth();
        hit(h, a, mana(Element.WATER).plus(mana(Element.LIGHTNING)));
        hit(h, b, mana(Element.LIGHTNING).plus(mana(Element.WATER)));
        h.assertTrue(
                a.getHealth() == before - 3.5f && b.getHealth() == before - 2.5f,
                "同次先水后雷导电，先雷后水不追溯");
        h.assertTrue(
                a.hasEffect(CombatEffects.DRENCHED) && b.hasEffect(CombatEffects.DRENCHED),
                "两种顺序最终都潮湿");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void queuedOldestCounterAndPriorDamage(GameTestHelper h) {
        var a = pig(h);
        var b = pig(h);
        float before = a.getHealth();
        var fire = mana(Element.FIRE);
        var water = mana(Element.WATER);
        var frost = mana(Element.FIRE, true, Fixed.ONE);
        hit(h, a, water.plus(fire).plus(frost));
        hit(h, b, fire.plus(frost).plus(water));
        h.assertTrue(
                a.getHealth() == before - 2
                        && a.getTicksFrozen() == 40
                        && !a.hasEffect(CombatEffects.DRENCHED),
                "水先消费火，剩余寒霜");
        h.assertTrue(
                b.getHealth() == before - .5f
                        && b.getTicksFrozen() == 0
                        && b.hasEffect(CombatEffects.DRENCHED),
                "火先消费寒霜，剩余水");
        reset(a);
        float after = a.getHealth();
        hit(h, a, water);
        h.assertTrue(a.getHealth() == after - .5f, "新一次命中不撤销上一发伤害");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void typesTagsAndNativeDamageCommand(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        h.assertTrue(registry.getHolder(SpellDamage.GENERIC).isPresent(), "通用ec_damage注册");
        for (var kind : DamageAffinity.values()) {
            var holder = registry.getHolderOrThrow(SpellDamage.type(kind));
            h.assertTrue(
                    holder.unwrapKey().orElseThrow().location().getPath().startsWith("ec_damage/"),
                    "全部元素类型统一前缀");
            var source = SpellDamage.source(h.getLevel(), null, kind);
            h.assertTrue(
                    source.is(DamageTypeTags.NO_KNOCKBACK)
                            && source.is(DamageTypeTags.BYPASSES_ARMOR),
                    "保留已有魔法伤害与独立风击退规则");
        }
        h.assertTrue(
                SpellDamage.source(h.getLevel(), null, DamageAffinity.FIRE)
                        .is(DamageTypeTags.IS_FIRE),
                "火伤接入原版火标签");
        h.assertTrue(
                SpellDamage.source(h.getLevel(), null, DamageAffinity.FROST)
                        .is(DamageTypeTags.IS_FREEZING),
                "霜伤接入原版冻结标签");
        var e = pig(h);
        float health = e.getHealth();
        h.getLevel()
                .getServer()
                .getCommands()
                .performPrefixedCommand(
                        h.getLevel()
                                .getServer()
                                .createCommandSourceStack()
                                .withLevel(h.getLevel())
                                .withSuppressedOutput(),
                        "damage " + e.getUUID() + " 1 elementalcircuits:ec_damage/arcane");
        h.assertTrue(e.getHealth() == health - 1, "原版damage命令直接识别注册ID");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void waterThenLightningAndMud(GameTestHelper h) {
        var e = pig(h);
        hit(h, e, mana(Element.WATER));
        h.assertTrue(e.hasEffect(CombatEffects.DRENCHED), "水留下原版潮湿状态");
        reset(e);
        float before = e.getHealth();
        hit(h, e, mana(Element.LIGHTNING));
        h.assertTrue(e.getHealth() == before - 3, "后续雷伤2变3");
        reset(e);
        hit(h, e, mana(Element.EARTH));
        h.assertTrue(e.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 1, "后续土形成泥泞II");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void steamAndThaw(GameTestHelper h) {
        var e = pig(h);
        e.addEffect(new MobEffectInstance(CombatEffects.DRENCHED, 120));
        e.setTicksFrozen(160);
        float before = e.getHealth();
        hit(h, e, mana(Element.FIRE));
        h.assertTrue(
                e.getHealth() == before - 1
                        && !e.isOnFire()
                        && !e.hasEffect(CombatEffects.DRENCHED),
                "湿目标火伤减半，不点燃并蒸干");
        h.assertTrue(e.getTicksFrozen() == 80, "火焰减少既有冻结");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void frostAndShatter(GameTestHelper h) {
        var e = pig(h);
        e.addEffect(new MobEffectInstance(CombatEffects.DRENCHED, 120));
        hit(h, e, mana(Element.FIRE, true, 2 * Fixed.ONE));
        h.assertTrue(e.getTicksFrozen() == 160, "潮湿使2魔力寒霜积累160冻结");
        reset(e);
        float before = e.getHealth();
        hit(h, e, mana(Element.EARTH));
        h.assertTrue(e.getHealth() == before - 1.5f && e.getTicksFrozen() == 80, "实际碎冰增伤并消耗冻结");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void armorAndMixedFireImmunity(GameTestHelper h) {
        var e = pig(h);
        e.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        float before = e.getHealth();
        hit(h, e, mana(Element.FIRE).plus(mana(Element.ARCANE)));
        h.assertTrue(e.getHealth() == before - 2 && !e.isOnFire(), "抗火只挡火，不吞奥术");
        var armored = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 2));
        armored.setNoAi(true);
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        before = armored.getHealth();
        hit(h, armored, mana(Element.ARCANE));
        h.assertTrue(armored.getHealth() == before - 2, "保持既有法术忽略护甲规则");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mixedOneEventAndNativeInvulnerability(GameTestHelper h) {
        var e = pig(h);
        var seen = new ArrayList<ElementalDamageSource>();
        Consumer<LivingIncomingDamageEvent> listener =
                event -> {
                    if (event.getEntity() == e
                            && event.getSource() instanceof ElementalDamageSource source)
                        seen.add(source);
                };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            float before = e.getHealth();
            hit(h, e, mana(Element.FIRE).plus(mana(Element.ARCANE)));
            h.assertTrue(e.getHealth() == before - 4 && seen.size() == 1, "混合只经过一次原版伤害事件");
            h.assertTrue(
                    seen.getFirst().is(SpellDamage.type(DamageAffinity.MIXED))
                            && seen.getFirst().components().size() == 2,
                    "混合ID和不可变分量保留");
            int invulnerable = e.invulnerableTime;
            before = e.getHealth();
            hit(h, e, mana(Element.WATER));
            h.assertTrue(
                    e.getHealth() == before
                            && !e.hasEffect(CombatEffects.DRENCHED)
                            && e.invulnerableTime == invulnerable,
                    "受击间隔保留，不附赠状态");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cancellationDoesNotLeakStatuses(GameTestHelper h) {
        var e = pig(h);
        Consumer<LivingIncomingDamageEvent> cancel =
                event -> {
                    if (event.getEntity() == e) event.setCanceled(true);
                };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            float health = e.getHealth();
            hit(h, e, mana(Element.FIRE).plus(mana(Element.WATER)).plus(mana(Element.WIND)));
            h.assertTrue(
                    e.getHealth() == health
                            && !e.hasEffect(CombatEffects.DRENCHED)
                            && !e.isOnFire()
                            && e.getDeltaMovement().lengthSqr() == 0,
                    "取消事件后伤害、潮湿、点燃和推力都不提交");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeStatusPersistenceMilkAndExpiry(GameTestHelper h) {
        var e = pig(h);
        hit(h, e, mana(Element.WATER));
        var tag = e.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var copy = pig(h);
        copy.load(tag);
        h.assertTrue(
                copy.hasEffect(CombatEffects.DRENCHED)
                        && copy.getEffect(CombatEffects.DRENCHED).getDuration() == 120,
                "NBT保存潮湿现场");
        var milk = new ItemStack(Items.MILK_BUCKET);
        milk.getItem().finishUsingItem(milk, h.getLevel(), copy);
        h.assertTrue(!copy.hasEffect(CombatEffects.DRENCHED), "牛奶清除潮湿");
        copy.discard();
        e.removeEffect(CombatEffects.DRENCHED);
        e.addEffect(new MobEffectInstance(CombatEffects.DRENCHED, 2));
        h.runAfterDelay(
                4,
                () -> {
                    h.assertTrue(!e.hasEffect(CombatEffects.DRENCHED), "原版逻辑步到期，不依赖法阵后台扫描");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void lightCountersShadowAndUndead(GameTestHelper h) {
        var e = pig(h);
        e.setHealth(4);
        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120));
        e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120));
        hit(h, e, mana(Element.LIGHT));
        h.assertTrue(
                e.getHealth() == 6
                        && !e.hasEffect(MobEffects.BLINDNESS)
                        && !e.hasEffect(MobEffects.DARKNESS),
                "光实际治疗并清除暗造成的效果");
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 2));
        zombie.setNoAi(true);
        float before = zombie.getHealth();
        hit(h, zombie, mana(Element.LIGHT));
        h.assertTrue(zombie.getHealth() == before - 4, "光伤亡灵而不治疗");
        reset(e);
        before = e.getHealth();
        hit(h, e, mana(Element.LIGHT).plus(mana(Element.SHADOW)));
        h.assertTrue(e.getHealth() == before && !e.hasEffect(MobEffects.BLINDNESS), "同量光暗中和");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void windFansButNeverShortensExternalFire(GameTestHelper h) {
        var e = pig(h);
        e.setRemainingFireTicks(40);
        hit(h, e, mana(Element.WIND));
        h.assertTrue(e.getRemainingFireTicks() == 60, "正风延长既有火势1秒");
        reset(e);
        e.setRemainingFireTicks(500);
        hit(h, e, mana(Element.WIND));
        h.assertTrue(e.getRemainingFireTicks() == 500, "风不截短外部更长火势");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void leatherBlocksFrostAndRealBeamCarriesType(GameTestHelper h) {
        var e = pig(h);
        e.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float before = e.getHealth();
        hit(h, e, mana(Element.FIRE, true, Fixed.ONE));
        h.assertTrue(e.getHealth() == before && e.getTicksFrozen() == 0, "原版皮革抗冻保护");
        var seen = new ArrayList<ElementalDamageSource>();
        Consumer<LivingIncomingDamageEvent> listener =
                event -> {
                    if (event.getEntity() == e
                            && event.getSource() instanceof ElementalDamageSource source)
                        seen.add(source);
                };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            Vec3 center = e.getBoundingBox().getCenter();
            var spec = new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, 0, 4, 0, 0);
            SpellEffects.cast(
                    h.getLevel(),
                    null,
                    center.add(0, 0, -1.5),
                    new Vec3(0, 0, 1),
                    UUID.randomUUID(),
                    new MagicCircuit.Cast(0, mana(Element.ARCANE), spec),
                    RuleResources.defaults());
            h.assertTrue(
                    seen.size() == 1 && seen.getFirst().is(SpellDamage.type(DamageAffinity.ARCANE)),
                    "真正光束命中经新元素伤害路径");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void frostWeaknessUsesOneCommonCap(GameTestHelper h) {
        var pure = h.spawn(EntityType.BLAZE, new BlockPos(1, 2, 2));
        var mixed = h.spawn(EntityType.BLAZE, new BlockPos(4, 2, 2));
        for (var e : List.of(pure, mixed)) {
            e.setNoAi(true);
            e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                    .setBaseValue(1000);
            e.setHealth(1000);
        }
        hit(h, pure, mana(Element.FIRE, true, 100 * Fixed.ONE));
        hit(h, mixed, mana(Element.FIRE, true, 100 * Fixed.ONE).plus(mana(Element.ARCANE)));
        h.assertTrue(
                Math.abs(pure.getHealth() - 872) < .001 && Math.abs(mixed.getHealth() - 872) < .001,
                "原版霜弱点只计一次，纯霜和混合都受128上限约束");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reactionFeedbackSharesExistingBudget(GameTestHelper h) {
        var e = pig(h);
        e.addEffect(new MobEffectInstance(CombatEffects.DRENCHED, 120));
        SpellVisuals.unload(h.getLevel());
        hit(h, e, mana(Element.FIRE));
        var pending = SpellVisuals.pending(h.getLevel());
        h.assertTrue(
                pending.size() == 1 && pending.getFirst().kind() == SpellVisual.Kind.STEAM,
                "蒸汽进入既有批量观察，不逐粒子发包");
        SpellVisuals.unload(h.getLevel());
        for (int i = 0; i < VisualBudget.WORLD_LIMIT + 5; i++)
            SpellVisuals.observe(
                    h.getLevel(),
                    SpellVisual.Kind.IMPACT,
                    Element.FIRE,
                    e.position().add(i, 0, 0),
                    e.position(),
                    1,
                    1);
        reset(e);
        e.addEffect(new MobEffectInstance(CombatEffects.DRENCHED, 120));
        float before = e.getHealth();
        hit(h, e, mana(Element.LIGHTNING));
        h.assertTrue(
                e.getHealth() == before - 3
                        && SpellVisuals.pending(h.getLevel()).size() <= VisualBudget.WORLD_LIMIT,
                "特效满载也不改变导电伤害");
        SpellVisuals.unload(h.getLevel());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeAndUnavailablePlayerOwnerRemainProtected(GameTestHelper h) {
        var p =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        h.getLevel(),
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "element-ward"));
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        float before = p.getHealth();
        hit(h, p, mana(Element.WATER).plus(mana(Element.FIRE)));
        h.assertTrue(
                p.getHealth() == before && !p.hasEffect(CombatEffects.DRENCHED) && !p.isOnFire(),
                "创造无敌不附赠元素状态");
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        ElementalCombatAdapter.apply(
                h.getLevel(), UUID.randomUUID(), p, mana(Element.WATER), Vec3.ZERO, Fixed.ONE);
        h.assertTrue(
                p.getHealth() == before && !p.hasEffect(CombatEffects.DRENCHED),
                "离线或失效施法者不能绕过玩家友伤校验");
        h.succeed();
    }
}
