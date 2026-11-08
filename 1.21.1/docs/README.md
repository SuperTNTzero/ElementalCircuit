# Documentation

Reference material for Elemental Circuits: circuit rules, mana, equipment, automation, art assets, and source organization. Most detailed documents are in Simplified Chinese. For installation and basic controls in English, start with the [mod guide](../README.md).

## Systems

| Topic | Reference |
| --- | --- |
| Standard costs, preparation time, and mana storage | [Standard state and dedicated storage](systems/标准状态与专用储魔.md) |
| Mana costs, refunds, recovery, and node dynamics | [Casting costs and mana dynamics](systems/施法成本与魔力动力学.md) |
| Crafting and spending rune items | [Rune items and engraving](systems/符文物品与篆刻.md) |
| Elemental damage and reaction order | [Elemental damage](systems/元素伤害与反应.md), [reaction queues](systems/元素反应队列.md) |
| Plates, overload, and meltdown | [Plate capacity and failure](systems/基板承载与熔断.md) |
| Circuit layers and derived spells | [Layered circuits](systems/同基板叠阵与编辑导航.md) |
| Contact casting, forces, constraints, and angles | [Contact casting and spatial parameters](systems/联系施法与空间参数.md) |
| Containers, material handling, and redstone | [Material and redstone automation](systems/物料与红石自动化.md) |
| Timing, filtering, and observation runes | [Routing, timing, and world observation](systems/分流时序与世界观察.md) |
| Spellbook and wand selection | [Arcane inversion and combined selection](systems/反相奥术与联合选阵.md) |
| Robes, rings, and equipment casting | [Equipment circuits](systems/法袍戒指与装备施法.md) |
| Fixed-circuit control and connection overlays | [Tuning Wand and monocle](systems/固定法阵调律与观阵.md) |
| Mana ecology and instant restoration | [Mana slimes and floating nodes](systems/魔力史莱姆与浮光节点.md) |
| Enchantments | [Magic enchantments](systems/魔法附魔.md) |
| In-game guide and optional Patchouli integration | [Circuit Field Guide](systems/法阵手札与Patchouli.md) |
| Server commands and performance limits | [Server configuration](systems/服务端配置与符文边界.md) |
| Custom animations and sounds | [The .ecfx package format](systems/自定义特效包.md) |

## Assets and source

| Directory or reference | Contents |
| --- | --- |
| [art](art/) | Models, textures, sound attribution, UI, and asset export references |
| [images](images/) | Screenshots grouped by feature |
| [promo](promo/) | Promotional images and storyboard material |
| [Source layout and formatting](maintenance/工程结构与排版.md) | Package responsibilities and Java conventions |
| [Text resource contract](maintenance/文本资源规范.md) | Localization keys and text validation |
| [Architecture](../ARCHITECTURE.md) | Domain, Minecraft adapter, persistence, and presentation boundaries |
| [Vector rune assets](../art/inkscape/README.md) | Editable SVG sources and exports |
| [Tools](../tools/README.md) | Build, test, and artwork commands |

## Versioned references

Documents under [verification](verification/) preserve test evidence for the version named in each record. [Maintenance](maintenance/) includes historical migration and code-review notes. Older documents may describe superseded behavior; use the current [mod README](../README.md), in-game guide, and source configuration for supported controls and defaults.
