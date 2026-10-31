package dev.elementalcircuits;

import dev.elementalcircuits.item.CircuitWandItem;
import dev.elementalcircuits.item.BlueprintNotebookItem;
import dev.elementalcircuits.item.SpellbookItem;
import dev.elementalcircuits.item.WandPartItem;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.adapter.WandResources;
import java.util.*;
import dev.elementalcircuits.network.CircuitNetwork;
import dev.elementalcircuits.server.ServerEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.*;

@Mod(ElementalCircuits.ID)
public final class ElementalCircuits {
    public static final String ID="elementalcircuits";
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,ID);
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final DeferredHolder<Item,Item> WAND=ITEMS.register("circuit_wand",()->new CircuitWandItem(new Item.Properties().durability(192)));
    public static final DeferredHolder<Item,Item> NOTEBOOK=ITEMS.register("blueprint_notebook",()->new BlueprintNotebookItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item,Item> SPELL_BOOK=ITEMS.register("spellbook",()->new SpellbookItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Block,Block> WAND_WORKBENCH=BLOCKS.register("wand_workbench",()->new WandWorkbenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS).strength(2.5f).noOcclusion()));
    public static final DeferredHolder<Item,Item> WORKBENCH_ITEM=ITEMS.register("wand_workbench",()->new BlockItem(WAND_WORKBENCH.get(),new Item.Properties()));
    public static final DeferredHolder<Item,Item> MANA_CRYSTAL=ITEMS.register("mana_crystal",()->new dev.elementalcircuits.item.ManaCrystalItem(new Item.Properties()));
    public static final DeferredHolder<Block,Block> MANA_ORE=BLOCKS.register("mana_crystal_ore",()->new DropExperienceBlock(net.minecraft.util.valueproviders.UniformInt.of(2,5),BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final DeferredHolder<Block,Block> DEEPSLATE_MANA_ORE=BLOCKS.register("deepslate_mana_crystal_ore",()->new DropExperienceBlock(net.minecraft.util.valueproviders.UniformInt.of(2,5),BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE)));
    public static final DeferredHolder<Item,Item> MANA_ORE_ITEM=ITEMS.register("mana_crystal_ore",()->new BlockItem(MANA_ORE.get(),new Item.Properties()));
    public static final DeferredHolder<Item,Item> DEEPSLATE_MANA_ORE_ITEM=ITEMS.register("deepslate_mana_crystal_ore",()->new BlockItem(DEEPSLATE_MANA_ORE.get(),new Item.Properties()));
    public static final DeferredHolder<Block,Block> NATURAL_MANA_NODE=BLOCKS.register("natural_mana_node",()->new ManaNodeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).strength(4f).requiresCorrectToolForDrops().lightLevel(s->s.getValue(ManaNodeBlock.LIT)?7:0).noLootTable()));
    public static final DeferredHolder<Block,Block> MANA_NODE=BLOCKS.register("mana_node",()->new ManaNodeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).strength(3f).lightLevel(s->s.getValue(ManaNodeBlock.LIT)?5:0).noLootTable()));
    public static final DeferredHolder<Item,Item> NATURAL_MANA_NODE_ITEM=ITEMS.register("natural_mana_node",()->new BlockItem(NATURAL_MANA_NODE.get(),new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item,Item> MANA_NODE_ITEM=ITEMS.register("mana_node",()->new BlockItem(MANA_NODE.get(),new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ManaNodeBlockEntity>> MANA_NODE_ENTITY=ENTITIES.register("mana_node",()->BlockEntityType.Builder.of(ManaNodeBlockEntity::new,NATURAL_MANA_NODE.get(),MANA_NODE.get()).build(null));
    public static final DeferredHolder<Item,Item> WOOD_PLATE=ITEMS.register("wood_plate",()->new Item(new Item.Properties()));
    public static final DeferredHolder<Item,Item> BRASS_PLATE=ITEMS.register("brass_plate",()->new Item(new Item.Properties()));
    public static final DeferredHolder<Item,Item> CRYSTAL_PLATE=ITEMS.register("crystal_plate",()->new Item(new Item.Properties()));
    public static final Map<String,DeferredHolder<Item,Item>> PARTS=new LinkedHashMap<>();
    static { for(var part:WandResources.definitions().parts())PARTS.put(part.id(),ITEMS.register(part.id(),()->new WandPartItem(part))); }
    public static final DeferredHolder<Block,Block> TABLE=BLOCKS.register("inscription_table",()->new InscriptionTable(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).strength(2.5f).noOcclusion()));
    public static final DeferredHolder<Block,Block> SPELL_LIGHT=BLOCKS.register("spell_light",()->new SpellLightBlock(BlockBehaviour.Properties.of().noCollission().noOcclusion().replaceable().noLootTable().lightLevel(s->15)));
    public static final DeferredHolder<Block,Block> DIMMED_LIGHT=BLOCKS.register("dimmed_light",()->new DimmedLightBlock(BlockBehaviour.Properties.of().strength(0.3f).noOcclusion().dynamicShape().noLootTable()));
    public static final DeferredHolder<Block,Block> CIRCUIT_BLOCK=BLOCKS.register("fixed_circuit",()->new CircuitBlock(BlockBehaviour.Properties.of().strength(2.5f,3600000f).noOcclusion().noLootTable()));
    public static final DeferredHolder<Item,Item> CIRCUIT_ITEM=ITEMS.register("fixed_circuit",()->new dev.elementalcircuits.item.FixedCircuitItem(CIRCUIT_BLOCK.get(),new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CircuitBlockEntity>> CIRCUIT_ENTITY=ENTITIES.register("fixed_circuit",()->BlockEntityType.Builder.of(CircuitBlockEntity::new,CIRCUIT_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<WorkstationBlockEntity>> WORKSTATION_ENTITY=ENTITIES.register("workstation",()->BlockEntityType.Builder.of(WorkstationBlockEntity::new,TABLE.get(),WAND_WORKBENCH.get()).build(null));
    public static final DeferredHolder<Item,Item> TABLE_ITEM=ITEMS.register("inscription_table",()->new BlockItem(TABLE.get(),new Item.Properties()));
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("circuits",()->CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.elementalcircuits"))
        .icon(()->new ItemStack(WAND.get()))
        .displayItems((parameters,output)->{output.accept(WAND.get());output.accept(WORKBENCH_ITEM.get());output.accept(NOTEBOOK.get());output.accept(SPELL_BOOK.get());output.accept(TABLE_ITEM.get());output.accept(CIRCUIT_ITEM.get());output.accept(MANA_CRYSTAL.get());output.accept(MANA_ORE_ITEM.get());output.accept(DEEPSLATE_MANA_ORE_ITEM.get());output.accept(NATURAL_MANA_NODE_ITEM.get());output.accept(MANA_NODE_ITEM.get());output.accept(WOOD_PLATE.get());output.accept(BRASS_PLATE.get());output.accept(CRYSTAL_PLATE.get());PARTS.values().forEach(part->output.accept(part.get()));}).build());
    public ElementalCircuits(IEventBus bus,ModContainer container){
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,dev.elementalcircuits.adapter.FeedbackConfig.SPEC);
        ITEMS.register(bus);BLOCKS.register(bus);ENTITIES.register(bus);TABS.register(bus);dev.elementalcircuits.adapter.persistence.CooldownComponent.TYPES.register(bus);
        dev.elementalcircuits.server.SpellSounds.register(bus);bus.addListener(CircuitNetwork::register);NeoForge.EVENT_BUS.register(ServerEvents.class);
    }
}
