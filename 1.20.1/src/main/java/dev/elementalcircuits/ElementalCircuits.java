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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(ElementalCircuits.ID)
public final class ElementalCircuits {
    public static final String ID="elementalcircuits";
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final RegistryObject<Item> WAND=ITEMS.register("circuit_wand",()->new CircuitWandItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NOTEBOOK=ITEMS.register("blueprint_notebook",()->new BlueprintNotebookItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SPELL_BOOK=ITEMS.register("spellbook",()->new SpellbookItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Block> WAND_WORKBENCH=BLOCKS.register("wand_workbench",()->new WandWorkbenchBlock(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE).strength(2.5f)));
    public static final RegistryObject<Item> WORKBENCH_ITEM=ITEMS.register("wand_workbench",()->new BlockItem(WAND_WORKBENCH.get(),new Item.Properties()));
    public static final RegistryObject<Item> WOOD_PLATE=ITEMS.register("wood_plate",()->new Item(new Item.Properties()));
    public static final RegistryObject<Item> BRASS_PLATE=ITEMS.register("brass_plate",()->new Item(new Item.Properties()));
    public static final RegistryObject<Item> CRYSTAL_PLATE=ITEMS.register("crystal_plate",()->new Item(new Item.Properties()));
    public static final Map<String,RegistryObject<Item>> PARTS=new LinkedHashMap<>();
    static { for(var part:WandResources.definitions().parts())PARTS.put(part.id(),ITEMS.register(part.id(),()->new WandPartItem(part))); }
    public static final RegistryObject<Block> TABLE=BLOCKS.register("inscription_table",()->new InscriptionTable(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE).strength(2.5f)));
    public static final RegistryObject<Block> SPELL_LIGHT=BLOCKS.register("spell_light",()->new SpellLightBlock(BlockBehaviour.Properties.of().noCollission().noOcclusion().replaceable().noLootTable().lightLevel(s->15)));
    public static final RegistryObject<Block> DIMMED_LIGHT=BLOCKS.register("dimmed_light",()->new DimmedLightBlock(BlockBehaviour.Properties.of().strength(0.3f).noOcclusion().dynamicShape().noLootTable()));
    public static final RegistryObject<Block> CIRCUIT_BLOCK=BLOCKS.register("fixed_circuit",()->new CircuitBlock(BlockBehaviour.Properties.of().strength(2.5f,3600000f).noLootTable()));
    public static final RegistryObject<Item> CIRCUIT_ITEM=ITEMS.register("fixed_circuit",()->new BlockItem(CIRCUIT_BLOCK.get(),new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<CircuitBlockEntity>> CIRCUIT_ENTITY=ENTITIES.register("fixed_circuit",()->BlockEntityType.Builder.of(CircuitBlockEntity::new,CIRCUIT_BLOCK.get()).build(null));
    public static final RegistryObject<Item> TABLE_ITEM=ITEMS.register("inscription_table",()->new BlockItem(TABLE.get(),new Item.Properties()));
    public static final RegistryObject<CreativeModeTab> TAB=TABS.register("circuits",()->CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.elementalcircuits"))
        .icon(()->new ItemStack(WAND.get()))
        .displayItems((parameters,output)->{output.accept(WAND.get());output.accept(WORKBENCH_ITEM.get());output.accept(NOTEBOOK.get());output.accept(SPELL_BOOK.get());output.accept(TABLE_ITEM.get());output.accept(CIRCUIT_ITEM.get());output.accept(WOOD_PLATE.get());output.accept(BRASS_PLATE.get());output.accept(CRYSTAL_PLATE.get());PARTS.values().forEach(part->output.accept(part.get()));}).build());
    public ElementalCircuits(){
        IEventBus bus=FMLJavaModLoadingContext.get().getModEventBus();ITEMS.register(bus);BLOCKS.register(bus);ENTITIES.register(bus);TABS.register(bus);
        dev.elementalcircuits.server.SpellSounds.register(bus);CircuitNetwork.register();MinecraftForge.EVENT_BUS.register(ServerEvents.class);
    }
}
