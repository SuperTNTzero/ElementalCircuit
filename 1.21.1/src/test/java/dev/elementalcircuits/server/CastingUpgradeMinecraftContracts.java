package dev.elementalcircuits.server;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.CastContext;
import dev.elementalcircuits.domain.circuit.SupplyMode;
import dev.elementalcircuits.domain.examples.StandardCircuit;
import dev.elementalcircuits.item.WizardArmorItem;
import dev.elementalcircuits.server.casting.WandCasting;
import dev.elementalcircuits.server.equipment.SpellAttributes;
import dev.elementalcircuits.server.equipment.WizardEquipment;
import dev.elementalcircuits.server.mana.CrystalAmplification;
import dev.elementalcircuits.server.mana.PlayerMana;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class CastingUpgradeMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h) {
        var p =
                new net.neoforged.neoforge.common.util.FakePlayer(
                        h.getLevel(),
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "charge-test"));
        p.setPos(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
        p.getInventory().selected = 0;
        return p;
    }

    private static ItemStack wand(ServerPlayer p) {
        var s = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().setItem(0, s);
        CarrierRepository.load(s)
                .replace(1, StandardCircuit.create(ServerSettings.circuitRules(), false));
        CarrierRepository.commit(s);
        return s;
    }

    @GameTest(template = "empty")
    public static void nativeTapReleasesSinglePulse(GameTestHelper h) {
        var p = player(h);
        var s = wand(p);
        s.getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(
                p.isUsingItem() && !CarrierRepository.load(s).circuit(1).powered(),
                "No supply starts on button down");
        s.getItem().onUseTick(h.getLevel(), p, s, 71998);
        p.releaseUsingItem();
        var c = CarrierRepository.load(s).circuit(1);
        h.assertTrue(
                c.powered() && c.supplyMode() == SupplyMode.PULSE && c.charge() == Fixed.ONE,
                "Native item release starts one uncharged invocation");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeHoldHasNoManaUntilRelease(GameTestHelper h) {
        var p = player(h);
        var s = wand(p);
        long mana = PlayerMana.get(p).available();
        s.getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        s.getItem()
                .onUseTick(
                        h.getLevel(), p, s, 72000 - ServerSettings.count("input.fullChargeTicks"));
        h.assertTrue(
                PlayerMana.get(p).available() == mana && !CarrierRepository.load(s).powered(),
                "Charging does not consume or cast");
        p.releaseUsingItem();
        var c = CarrierRepository.load(s).circuit(1);
        h.assertTrue(
                c.charge() == ServerSettings.mana("input.maximumCharge")
                        && JsonCodecs.readSnapshot(JsonCodecs.snapshot(c)).charge() == c.charge(),
                "Charged invocation remains frozen in native item persistence");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void changingSlotCancelsCharge(GameTestHelper h) {
        var p = player(h);
        var s = wand(p);
        s.getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        s.getItem().onUseTick(h.getLevel(), p, s, 71950);
        p.getInventory().selected = 1;
        WandCasting.tick(p);
        p.releaseUsingItem();
        h.assertTrue(
                !CarrierRepository.load(s).powered(), "Switching away cannot release the old wand");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void playerManaCostAndRobeReduction(GameTestHelper h) {
        var p = player(h);
        h.assertTrue(
                p.getAttributeValue(SpellAttributes.MANA_COST) == 1
                        && p.getAttributeValue(SpellAttributes.MANA_REDUCTION) == 0,
                "Base attributes are neutral");
        double reduction = 0;
        for (var item :
                List.of(
                        WizardEquipment.HAT,
                        WizardEquipment.ROBE,
                        WizardEquipment.LEGGINGS,
                        WizardEquipment.BOOTS))
            for (var e : ((WizardArmorItem) item.get()).getDefaultAttributeModifiers().modifiers())
                if (e.attribute().equals(SpellAttributes.MANA_REDUCTION)) {
                    reduction += e.modifier().amount();
                    p.getAttribute(SpellAttributes.MANA_REDUCTION)
                            .addTransientModifier(e.modifier());
                }
        h.assertTrue(reduction == 22, "Four armor pieces grant 4+8+6+4 percent");
        p.getAttribute(SpellAttributes.MANA_COST).setBaseValue(.5);
        h.assertTrue(
                Math.abs(SpellAttributes.context(p).manaMultiplier() / (double) Fixed.ONE - .39)
                        < .0001,
                "Base cost and armor multiply without changing damage");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void crystalBoostIsLocalAndConservesNodeEnergy(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, ElementalCircuits.MANA_NODE.get().defaultBlockState());
        var node = (ManaNodeBlockEntity) level.getBlockEntity(pos);
        node.insert();
        long plain = node.emission(), stock = node.remaining();
        level.setBlockAndUpdate(
                pos.below(), ElementalCircuits.MANA_CRYSTAL_BLOCK.get().defaultBlockState());
        long boosted = node.emission();
        h.assertTrue(boosted > plain, "Adjacent crystal increases node throughput");
        node.emitted(boosted);
        h.assertTrue(
                node.remaining() == stock - boosted, "Artificial node pays all amplified emission");
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        h.assertTrue(node.emission() == plain, "Removing support immediately removes boost");
        h.assertTrue(
                CrystalAmplification.context(level, pos).equals(CastContext.NEUTRAL),
                "Unboosted fixed casting is neutral");
        level.setBlockAndUpdate(
                pos.below(), ElementalCircuits.MANA_CRYSTAL_BLOCK.get().defaultBlockState());
        var context = CrystalAmplification.context(level, pos);
        h.assertTrue(
                context.damageMultiplier() > Fixed.ONE
                        && context.damageMultiplier() == context.manaMultiplier()
                        && context.speed() > Fixed.ONE,
                "Fixed circuit boost also pays its higher power cost");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void naturalNodeRegenerationIsAmplified(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, ElementalCircuits.NATURAL_MANA_NODE.get().defaultBlockState());
        var node = (ManaNodeBlockEntity) level.getBlockEntity(pos);
        long amount = node.emission();
        node.emitted(amount);
        long start = node.remaining();
        level.setBlockAndUpdate(
                pos.below(), ElementalCircuits.MANA_CRYSTAL_BLOCK.get().defaultBlockState());
        node.regenerate(Fixed.ONE, 20);
        h.assertTrue(
                node.remaining() - start
                        == Math.min(
                                amount,
                                CrystalAmplification.scale(
                                        level,
                                        pos,
                                        Fixed.ONE,
                                        "crystal.naturalRecoveryMultiplier")),
                "Natural recovery uses crystal multiplier without overfilling");
        h.succeed();
    }
}
