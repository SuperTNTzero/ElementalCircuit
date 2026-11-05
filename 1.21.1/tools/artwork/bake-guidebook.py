"""Build both readers from the same bilingual field notes. No runtime content generation."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'src/main/resources'
NS = 'elementalcircuits:'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

CATEGORIES = [
    ('beginning', '第一张法阵', 'First circuit', 'circuit_wand'),
    ('workshop', '刻印与装配', 'Workshop', 'inscription_table'),
    ('runes', '符文与叠阵', 'Runes and layers', 'crystal_plate'),
    ('field', '水晶与魔力场', 'Crystals and mana', 'mana_node'),
    ('equipment', '随身装备', 'Equipment', 'wizard_hat'),
    ('reference', '查阅与排错', 'Field reference', 'probe_wand'),
]

# id, category, item icon, Chinese title, English title, Chinese notes, English notes, recipe ids
ENTRIES = [
('first_steps','beginning','circuit_guide','从这里开始','Start here',
 ['先备好法杖、法阵工作台和木质基板。法杖工作台用于换部件，法阵工作台用于刻印。两张桌子的用途不同。','手持法杖与法阵工作台互动，进入编辑器。把输入、元素转化、形状和输出按顺序连接，保存后离开工作台试射。'],
 ['Prepare a wand, an inscription table and a wooden board. The wand workbench changes parts; the inscription table draws circuits.','Use a wand on the inscription table. Connect input, conversion, shape and output, save, then step away to test your spell.'],['circuit_guide']),
('first_cast','beginning','circuit_wand','一次施法','Your first cast',
 ['用魔力输入→元素转化（火）→球形→输出搭出一条通路。魔力量、射程、半径和精度另用参数符文调整。先从小范围测试。','单击右键瞬发；按住右键蓄力，松手释放。默认按住6刻后进入蓄力，40刻蓄满，最高2倍强度，输出成本同步增加。按住期间不注入魔力。'],
 ['Build input → fire conversion → sphere → output. Separate parameter runes set amount, range, radius and precision. Test a small area first.','Tap right-click to cast; hold to charge and release to cast. Defaults: charging starts at tick 6, full charge at tick 40, up to 2× strength and output cost. Holding alone spends no input.'],['circuit_wand']),
('supply','beginning','mana_crystal','输入与魔力槽','Input and reserve',
 ['输入符文设置每次注入量。它是固定输入，不会因法杖较弱而偷偷缩小。施法前检查玩家魔力是否足够；大量输入也受服务器数值范围约束。','参数先确定，输出再核算成本。魔力不足会施法失败，按元素返还系数和来源账本退回可返还部分。连线损耗、自然衰减与失败损失不退还。'],
 ['The input rune sets a fixed amount per injection. A weaker wand does not silently shrink it. Check your mana reserve; server numeric limits still apply.','Parameters are resolved before output cost. Insufficient mana fails the cast. Refunds follow each element’s configured ratio and source ledger; transmission, decay and failure losses stay spent.'],[]),
('drawing','workshop','inscription_table','在工作台刻印','Draw at the table',
 ['左侧按分类展开符文，预览后放入格盘。工具栏切换选择与布线。选中符文可拖动；选中参数后在按钮下方编辑，回车确认该参数。','连线连接明确的两个符文，允许非相邻连接。经过格数与基板共同影响损耗，线条交叉不会自动接通。只有布线模式下，指向单根连线按Delete才删除它。'],
 ['Expand rune categories on the left, preview and place runes. Switch between selection and wiring. Drag a selected rune; edit its parameters below the button and press Enter.','Links join explicit runes, including distant ones. Crossed cells and board material affect loss; crossing wires never joins them. In wire mode only, hover a link and press Delete to remove that link.'],['inscription_table']),
('boards','workshop','brass_plate','基板与固定法阵','Boards and fixed circuits',
 ['基板管理选择木、黄铜或水晶。材质决定格盘、损耗和派生深度。槽位对应不同基板，同一基板上的主阵和叠阵共用材质与尺寸。','手持基板与法阵工作台互动，绘制并保存后才得到固定法阵。放置后它在世界中独立运行。天然节点和魔力场用于持续供能；不要把未刻印基板当成成品。'],
 ['Board management offers wood, brass and crystal. Material controls grid, loss and recursion depth. Slots hold separate boards; layers on one board share material and size.','Use a blank board on the inscription table, draw and save to obtain a fixed circuit. Place it for independent world operation and arrange field supply nearby. Blank boards are not finished circuits.'],['wood_plate','brass_plate','crystal_plate']),
('parts','workshop','wand_workbench','装配法杖','Assemble a wand',
 ['手持法杖与法杖工作台互动。杖端决定法术强度、速度和耐载阈值；杖身与杖端共同决定耐久；杖尾决定近战伤害。更换部件需要背包里有对应实物。','施法冷却由部件决定，法阵复杂度还会影响准备时间。分支、长线和反馈会增加复杂度。高强度或高速法术超过杖端阈值后磨损上升；创造模式不扣耐久。'],
 ['Use your wand on the wand workbench. The head sets power, speed and wear thresholds; head and shaft set durability; the tail sets melee damage. Replacement parts must be in your inventory.','Parts determine cooldown; circuit complexity also adds preparation time. Branches, long links and feedback increase complexity. Exceeding head thresholds adds wear. Creative casting does not consume durability.'],['wand_workbench','copper_head','iron_body','iron_tail']),
('sharing','workshop','blueprint_notebook','蓝图簿与分享','Blueprint notebook',
 ['蓝图保存法阵设计，不保存正在运行的魔力。编辑器顶部“分享”导出并复制文本码；导入时粘贴完整文本码。先保留原蓝图，再尝试别人的设计。','蓝图簿可保存多份设计并预览。右键打开簿子，收录、命名、选择或删除蓝图。在工作台将蓝图应用到法阵，仍需符合当前载体的材质和尺寸。'],
 ['Blueprints save designs, not live mana. Use Share at the top of the editor to export and copy a text code; paste a complete code to import. Keep a copy before experimenting.','The notebook stores named designs with circuit previews. Open it to add, select, rename or delete pages. Apply a blueprint at the table; it must fit the carrier’s material and dimensions.'],['blueprint_notebook']),
('elements','runes','mana_crystal','元素是独立符文','Element conversion',
 ['元素转化与形状分别设置。火与水能在允许的位置造火、造水；光产生临时光源并照亮弹道，暗会熄灭光源，正相奥术可穿透方块。','反相奥术在同次命中中按到达顺序等量抵消其他元素，包括它们的反相；不抵消纯净魔力，剩余反相奥术不造成奥术伤害。雷配光束表现为闪电链，火配扇形表现为喷火。世界交互仍需有效目标并遵守权限。'],
 ['Conversion and shape are separate runes. Fire and water can create fire and water; light creates temporary illumination, darkness extinguishes lights, and positive arcane can pass through blocks.','Inverse arcane cancels other elements, including their inverses, one-to-one in arrival order within one contact. Pure mana is unaffected; excess negation deals no arcane damage. Lightning beams chain; fire fans spray flame. Valid targets and permissions still apply.'],[]),
('shapes','runes','crystal_plate','形状与影响范围','Shapes and volume',
 ['球、光束、盾牌之外，还有扇形、墙、环、柱、十字和穹顶。范围符文设置射程，半径符文设置宽度或范围；两者不是免费的强度倍率。','形状成本统一按影响的方块数估算，再乘可配置系数。先补齐默认参数再报价。大范围、高法强和蓄力都会提高输出要求，先用小规模检查魔力收支。'],
 ['Alongside sphere, beam and shield are fan, wall, ring, column, cross and dome. Range sets reach; radius sets width or area. Neither is a free damage multiplier.','Shape cost is based on affected block count and configurable coefficients. Defaults are resolved before quoting. Large volumes, spell power and charging need more output mana. Start small.'],[]),
('control','runes','wood_plate','控制与时序','Control and timing',
 ['与、或、反、开关和感应器处理魔力或信号；两种载荷通过明确的接口关联。计数器记录触发次数，节拍器提供时序，状态符文可保存开关状态。','信号状态不等于魔力储量。用信号控制支路、改变元素或安排轮换施法；先观察端口和信号方向，再检查魔力通路。复杂控制建议先拆成几段验证。'],
 ['AND, OR, NOT, switches and sensors process mana or signals through explicit interfaces. Counters count triggers, clocks provide timing, and state runes retain control state.','Signal state is not stored mana. Use signals to gate branches, change elements or alternate spells. Check ports and signal direction separately from mana paths. Test complex control in stages.'],[]),
('storage','runes','mana_node','存储与蓄能','Storage and charging',
 ['普通符文不会把所有经过的魔力无限留住。存储符文有容量、释放量、释放阈值三个设置，可多次或持续注入，达到阈值后送出指定量。','也可以用信号控制释放。检查容量是否容得下阈值、释放量是否能支付输出。缓存魔力自然衰减；它适合缓冲和蓄能，不能无损永久保存。'],
 ['Ordinary runes do not retain every passing packet. A storage rune has capacity, release amount and release threshold. Repeated or continuous input can fill it before releasing a batch.','Signals can also control release. Capacity must accommodate the threshold; released batches must cover output cost. Stored mana decays naturally, so storage is a buffer rather than a lossless vault.'],[]),
('derivatives','runes','spellbook','命中后的法术','Impact and layers',
 ['分裂、连锁、爆发、弹跳、追踪以及附加效果归入独立分类。它们改变投射物行为或命中后的派生过程，仍需支付对应成本并遵守性能预算。','需要更复杂的后续效果时，在叠阵关系页新建同一基板上的子阵，并明确选择命中或定时触发。子阵从触发位置释放；递归受材质公式、深度和总激活预算限制。'],
 ['Fragmentation, chaining, bursts, bounces, homing and added effects have their own categories. They change projectile behavior or impact results and still pay costs within performance budgets.','For another circuit after impact, create a child layer on the same board and explicitly choose hit or timer triggering. Children cast at the trigger location. Material depth and total activation budgets bound recursion.'],[]),
('crystals','field','mana_crystal_ore','寻找魔力水晶','Find mana crystals',
 ['地下的魔力水晶矿有石质与深板岩两种。准备合适的镐采掘，获得魔力水晶。水晶用于节点、装备、魔力果实、酿造和水晶块。','天然节点与矿石不同：节点储存和补充魔力。用探测杖读出节点信息；想长久供能，优先围绕节点安排汲取和固定法阵。'],
 ['Mana crystal ore occurs in stone and deepslate underground. Mine it with a suitable pickaxe. Crystals are used for nodes, equipment, fruit, brewing and crystal blocks.','Natural nodes are not ore: they hold and replenish mana. Read them with the probe wand and arrange extraction and fixed circuits around them for sustained supply.'],[]),
('nodes','field','mana_node','节点与汲取','Nodes and absorption',
 ['天然节点会自然恢复，人工节点需要补入水晶。节点向周围魔力场释放能量；汲取符文从所在位置取得魔力，不能凭空创造能量。','多个汲取口能提高吞吐，但节点储量、自然补能、场浓度和释放上限仍会限制供能。集中汲取会让局部浓度下降，增加汲取口不等于无限供给。'],
 ['Natural nodes recover over time; artificial nodes need crystal refills. Nodes emit into the local mana field. Absorption runes draw from their location rather than creating free energy.','More intake runes can raise throughput, but reserves, replenishment, field concentration and emission limits remain. Heavy extraction lowers local concentration; extra intakes do not mean unlimited supply.'],['mana_node']),
('amplification','field','mana_crystal_block','水晶基座','Crystal foundations',
 ['九枚魔力水晶可压成一块，也可拆回九枚。把水晶块紧贴放在节点或固定法阵正下方生效；隔层、侧邻和继续向下堆叠不额外叠加。','默认节点释放上限提高50%，天然节点恢复提高50%。固定法阵的强度、成本和准备速度系数提高25%。增幅不会赠送魔力，人工节点也不会因此自然恢复。'],
 ['Craft nine crystals into one block, or unpack it back into nine. Place it directly below a node or fixed circuit. Gaps, side placement and deeper stacks add no further bonus.','Defaults: node emission +50%, natural recovery +50%; fixed-circuit power, cost and preparation speed coefficients +25%. The block creates no free mana and does not grant artificial nodes natural recovery.'],['mana_crystal_block','mana_crystal_from_block']),
('growth','field','mana_fruit','魔力果实与药水','Fruit and potions',
 ['魔力水晶与苹果合成魔力果实，食用可永久提高玩家魔力上限，默认成长上限1024。属性和服务器设置仍可调整实际容量。','粗制药水加入魔力水晶，酿成魔力恢复药水。红石延长时间，萤石粉提高等级。药水只提高恢复公式中的固定值部分，百分比恢复项不受它放大。'],
 ['Craft a mana fruit from an apple and mana crystals. Eating it permanently raises reserve capacity, up to the default growth cap of 1024. Attributes and server settings can adjust capacity.','Brew a mana crystal into an awkward potion for Mana Recovery. Redstone extends duration; glowstone improves level. The effect boosts only the fixed part of regeneration, not its percentage term.'],['mana_fruit']),
('spellbook','equipment','spellbook','用书切换法阵','Carry a spellbook',
 ['法术书本身保存法阵，最多收录四张基板。副手持书时，书中法阵加入主手法杖的轮盘；杖阵仍可选用。轮盘用“杖”和“书”区分来源，法杖提供部件属性并承担磨损。','默认Z、X依次切换杖阵和书阵，按住R打开轮盘，B打开法术书。收起或更换书会回到本杖，重新持有原书可恢复选择。各槽位保留独立现场，切换不会复制或补充魔力。'],
 ['A spellbook stores up to four boards. In the offhand, it adds its circuits to the main-hand wand’s wheel without hiding wand circuits. Entries identify their source. The wand provides stats and takes wear.','Z/X cycle across both sources; hold R for the wheel; B opens the book. Removing or swapping the book falls back to the wand. Holding the original book again restores its choice. Each slot retains its own state; switching never copies or refills mana.'],['spellbook']),
('robes','equipment','wizard_robe','法袍与装备施法','Robes and casting',
 ['皮革与魔力水晶制作帽、法袍、绑腿和靴子。它们提高法强、魔力容量和固定恢复量，也降低魔力消耗。默认四件减免为4%、8%、6%、4%，合计22%。','装备也可在法阵工作台铭刻。默认V打开装备轮盘，J使用选中装备法阵；可配置受击触发施法。受击触发仍受魔力与运行限制，不是无代价的反击。'],
 ['Leather and mana crystals make the hat, robe, leggings and boots. They add spell power, capacity and fixed regeneration, and reduce mana cost by 4%, 8%, 6% and 4%: 22% together.','Inscribe equipment at the inscription table. V opens the equipment wheel; J casts the selected equipment circuit. An on-hit casting mode is available and still respects mana and runtime limits.'],['wizard_hat','wizard_robe','wizard_leggings','wizard_boots']),
('ring','equipment','circuit_ring','铭刻戒指','Inscribe a ring',
 ['戒指通过Curios的戒指槽装备，并可在法阵工作台铭刻法阵。默认H使用戒指法阵，相关键位可在控制设置中修改。','戒指与法袍、法术书各有自己的法阵数据。换装备不会合并魔力槽：玩家供能仍来自同一个玩家储备，连续施法时注意总消耗。'],
 ['Equip the ring in a Curios ring slot and inscribe it at the inscription table. H casts its circuit by default; controls are rebindable.','The ring, robes and spellbook each retain their own circuit data. They share the player’s mana reserve, so account for total demand when casting from several items.'],['circuit_ring']),
('probe','reference','probe_wand','探测与定位','Inspect and locate',
 ['探测杖负责读取固定法阵、节点等对象的信息。对着目标右键查看状态；右键空地探测附近天然节点，默认水平搜索半径500格，可由服务器配置。','探测结果用于决定在哪里建设，不会向玩家免费补能，也不会让人工节点冒充天然节点。范围越大，搜索成本越高；大型服务器可调整预算与半径。'],
 ['Use the probe wand on a fixed circuit or node to inspect it. Use it away from a target to survey natural nodes nearby, with a default horizontal radius of 500 blocks, configurable by the server.','Surveys help choose a building site. They do not refill mana or count artificial nodes as natural ones. Large searches cost more work; server owners can tune radius and budgets.'],['probe_wand']),
('reactions','reference','mana_crystal','元素反应顺序','Reaction order',
 ['混合元素按法阵实际到达顺序排队反应。同次命中先完成反应，剩余元素量再造成伤害和附加效果。等量相克元素可以互相抵消，避免把两份伤害都先结算。','已经由前一发法术造成的伤害不会被后一发撤销。伤害遵循原版受击间隔，快速命中不保证每次都扣血。排错时同时观察元素顺序、间隔和实际剩余量。'],
 ['Mixed elements react in their actual circuit arrival order. For one hit, reactions resolve first; only remaining elements apply damage and effects. Opposed equal amounts can cancel before either deals damage.','A later projectile does not undo damage already dealt. Vanilla hurt immunity applies, so rapid contacts may not all damage a target. Check order, timing and remaining quantities together.'],[]),
('economy','reference','circuit_guide','理解魔力成本','Read the mana budget',
 ['标准状态提供默认参数和准备时间的基准。实际成本由形状体积、法强、玩家基础魔耗、装备减免和蓄力共同决定；连线、符文及缓存损耗另行记账。','法强提高伤害，也按成本指数提高需求。基础魔耗与减免在玩家属性中独立设置。降低输出成本不等于降低固定输入；多出的可返还部分按来源账本处理。'],
 ['The standard state anchors default parameters and preparation time. Shape volume, spell power, base mana cost, equipment reduction and charge determine output cost; link, rune and storage losses are accounted separately.','Spell power increases damage and demand through the cost exponent. Base cost and reduction are separate player attributes. Cheaper output does not shrink fixed input; eligible surplus follows source-ledger refunds.'],[]),
('troubleshooting','reference','probe_wand','法阵没有施法时','When a circuit will not cast',
 ['依次检查：输入能否支付；通路是否连到输出；与门两侧是否齐备；开关和信号方向是否正确；存储阈值是否能达到；输出范围是否超过现有魔力。','再检查法杖是否损坏、是否仍在冷却或准备、子阵触发条件是否明确、蓝图是否适合当前基板。参数框展开时回车只保存参数，再按回车才保存整阵。'],
 ['Check in order: affordable input, a path to output, both AND inputs, switch state and signal direction, reachable storage threshold, and enough mana for the chosen volume.','Then check wand wear, cooldown, preparation, explicit child triggers and board fit. With a parameter editor open, Enter saves that parameter first; press Enter again to save the circuit.'],[]),
('controls','reference','circuit_guide','查找设置','Find settings',
 ['这里记录的是默认键位和默认数值，整合包可以修改。键位以游戏控制设置为准；服务器配置可调整输入、蓄力、魔力场、递归深度和性能预算。','有权限的管理员可用/ec管理命令调整魔力和配置；/attribute可调整法强、基础魔耗与减免。指南只阅读，不执行管理命令，也不改变已有法阵现场。'],
 ['These are default controls and values; modpacks may change them. Check Controls for current bindings. Server configuration covers charge, mana fields, recursion depth and performance budgets.','Authorized admins can use /ec management commands and /attribute for power, base mana cost and reduction. Reading this guide never executes commands or changes circuit state.'],[]),
]

ENTRIES.extend(
[
    [
        "enchantments",
        "equipment",
        "minecraft:enchanted_book",
        "魔法附魔",
        "Magic enchantments",
        [
            "扩容、源流、增幅均为III级：每级分别增加32魔力容量、每秒0.5固定回复、0.1法强。法强同时影响伤害与施法成本。支持法杖、法术书、法袍和戒指；手持或佩戴时生效，扩容不补满魔力。",
            "迅咒III：当前用于施法的法杖、法袍部件或戒指每级提高10%速度，同时缩短释放冷却和复杂度延迟。护板III：当前承载法阵的物品每级减少15%基板磨损，不改变过载和熔毁阈值。",
            "土、水、火、风、雷、光、暗、奥术、霜各有IV级庇护。仅穿戴护甲与戒指生效，每级抵御对应魔法伤害4%，累计最多80%；同件物品不能共存不同庇护。混合伤害按元素占比计算，反应和原版受击间隔仍然有效。",
            "魔力维修II是宝藏附魔，与经验修补互斥。每秒每级最多修复1耐久，每点消耗4魔力。手持或佩戴时优先修物品，再修各槽基板，小数按实际量扣魔。背包闲置不修，熔毁仍须工作台换板，修复不自动起阵。",
            "普通魔法附魔可通过附魔台、铁砧、村民交易及随机附魔书获取；魔力维修只出现在宝藏附魔渠道。创造栏提供全部15种最高级附魔书。具体生成机会受原版战利品和交易规则影响。"
        ],
        [
            "Mana Capacity, Mana Recovery and Spell Power reach III: each level adds 32 capacity, 0.5 fixed mana per second and 0.1 spell power respectively. Power affects both damage and mana cost. Wands, spellbooks, robes and rings apply these while held or worn; capacity never refills mana.",
            "Swift Casting III adds 10% speed per level to the actual casting wand, armor piece or ring, shortening release cooldown and complexity delay. Board Preservation III reduces board wear by 15% per level on its carrier. Overload and meltdown thresholds stay the same.",
            "Earth, Water, Fire, Wind, Lightning, Light, Shadow, Arcane and Frost Wards reach IV. Worn armor and rings give 4% protection per level against that magical element, capped at 80%. Wards are mutually exclusive per item. Mixed hits use damage shares; reactions and vanilla hurt immunity still apply.",
            "Mana Mending II is a treasure enchantment incompatible with XP Mending. Each level repairs up to 1 durability per second at 4 mana per point, on held or worn items only. Item wear is repaired before circuit boards, including fractional board wear. Melted boards need replacement at a table. Repair never starts a circuit.",
            "Ordinary magic enchantments join the vanilla table, anvil, trading and random enchanted-book pools. Mana Mending uses treasure channels. All 15 maximum-level books are in the creative tab; actual availability follows vanilla loot and trade rules."
        ],
        []
    ],
    [
        "mana_ecology",
        "field",
        "mana_gel",
        "魔力史莱姆与浮光",
        "Mana Slimes and Aerial Nodes",
        [
            "浮光节点自然悬在地表上方8—16格，呈蓝白光团。默认储量1024、每秒恢复2、每场步输出1；地脉节点则储量4096、每秒恢复1、每场步输出0.5。两者均可为魔力场补能，探测杖会区分它们，范围勘测会统计两种天然节点。",
            "天然节点24格内可能生成魔力史莱姆；普通史莱姆在场强至少24的区域连续停留30秒后也会转化，离开强场则重新计时。小型魔力史莱姆掉落魔力凝胶，食用立即恢复16魔力。粗制药水＋凝胶酿成魔力复原药水，瞬间恢复32魔力，萤石强化后恢复64；已有魔力回复药水仍只提高持续回复速度。",
            "凝胶直接食用或用于酿造。"
        ],
        [
            "Aerial Mana Nodes hover 8–16 blocks above the surface as blue-white orbs. Defaults: 1024 reserve, 2 mana regenerated per second and 1 emitted per field step. Ground nodes hold 4096, regenerate 1 per second and emit 0.5 per step. Both feed the mana field and are counted by the probe survey.",
            "Mana Slimes may spawn within 24 blocks of natural nodes. A normal slime converts after 30 continuous seconds in a field cell holding at least 24 mana. Small Mana Slimes drop Mana Gel. Eat it to restore 16 mana, or brew it into an Awkward Potion for a Potion of Mana Restoration: 32 mana instantly, or 64 with glowstone. Existing Mana Recovery potions still accelerate regeneration instead.",
            "Eat gel directly or use it for brewing."
        ],
        []
    ]
]
)

ENTRIES.append(('material_automation', 'runes', 'fixed_circuit', '物料与红石', 'Materials and redstone',
    ['反相土能拆解普通方块，默认成本为1加方块硬度。无附魔钻石镐的正常掉落留在原地；基岩、流体及有独立状态的容器或机器不会被拆解。',
     '正相土配合容器读取，从固定法阵紧邻的容器提取真实方块来放置。拆解通路加入容器输出，可把产物送回容器；装不下的掉在原地。首期放置限普通单格实心方块，手持法杖不隐式读取背包。',
     '容器符文沿用参数栏：0下、1上、2北、3南、4西、5东，采用世界方向。同一输出不能合流两个不同读取方向。库存、权限或锚点失效时不会复制物品。',
     '红石感应输出信号，阈值1–15，默认1。接信号模式的通路开关即可控制供魔，也可组合计数、反相及锁存。先手动启动持续法阵；低电平不等于停阵，红石不会重启已停阵的载体。'],
    ['Inverse earth dismantles ordinary blocks at a default effective-mana cost of 1 plus hardness. Normal unenchanted diamond-pickaxe loot stays at the target. Bedrock, fluids and stateful containers or machines are excluded.',
     'Combine normal earth with Container Read to place real blocks from a container immediately adjacent to the fixed circuit. Add Container Output to a dismantling path to collect its loot; overflow remains on the ground. Initial placement supports ordinary single-cell solid blocks. Wands do not implicitly read player inventory.',
     'Use the existing rune parameter: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east, in world coordinates. Conflicting input sides cannot merge. Missing stock, permissions or anchors never create items.',
     'Redstone Sensor outputs a circuit signal at a threshold of 1–15, default 1. Wire it to a signal-controlled switch, or combine counters, inversion and latches. Manually start the continuous circuit first. Low power is a valid low signal; redstone never restarts stopped circuits.'], []))

ENTRIES.append(('connections', 'runes', 'circuit_wand', '联系与操作', 'Connections and operations',
    ['联系是独立的施法方式，与指向、范围并列。它只锁定瞄准的实体或方块，不会自动抓取、伤害或开启容器；不要搭配形状符文。每个输出独立保留目标，续供不随准星换目标。',
     '接入施力，可沿出射方向推物；使用反相魔力则牵引。接入约束，可用有限弹力维持目标与施法源的相对距离。调整视线即可移动参考点，联系距离可另设；实体仍有重力和碰撞，不能穿墙搬运。',
     '交互效果需要信号线：每次低电平变高，操作所联系的拉杆、按钮或钟一次；持续高电平不会反复切换。它不会替代容器读取和输出，库存搬运仍使用原有物料符文。',
     '含联系符文的持续法阵，在右键点击或蓄力释放后持续供给，再次右键停止；其他法阵保持单次手势。魔力先支付每条联系的距离、负载维护费，再支付实际施力或交互费用；余额不能退回或作为阵内魔力使用。停阵、断供、目标失效、遮挡或超距会解除。服务器contact配置控制预算和费用。'],
    ['Connection is a casting method alongside Directed and Area. It only binds the aimed entity or block; it grants no implicit grabbing, damage or inventory access. Do not add shape runes. Each output retains its own target across deliveries.',
     'Force pushes along the casting direction; inverse mana pulls. Constraint uses finite spring force to hold an entity relative to the source. Aim to move the reference point, or set its distance separately. Gravity and collisions remain active; entities cannot be carried through walls.',
     'Interaction requires a signal wire. Each low-to-high transition operates the linked lever, button or bell once. A held high signal never toggles repeatedly. Inventory operations still require Container Read and Container Output.',
     'A continuous circuit containing Connection keeps supplying after click or charged release; right-click again to stop it. Other circuits retain single-shot gestures. Each link pays distance and load maintenance, then actual force work or interaction. Its remaining allowance cannot be refunded or used as circuit mana. Stopping, loss of supply, invalid targets, obstruction or excess range releases it. Server contact settings control costs and budgets.'], []))

ENTRIES.append(('spatial_parameters', 'runes', 'crystal_plate', '角度与方向', 'Angles and direction',
    ['角度符文设置扇形法术的完整张角：1—180度，默认70度。小数可连续输入。张角同时改变实际作用范围、按方块足迹计算的消耗和客户端特效，不改变半径与距离的含义。首期仅用于扇形，其他形状不会静默套用。',
     '偏转设置相对瞄准方向的水平角度：-180—180度，正值向右。俯仰范围-90—90度，正值向上。它们作用于出射方向，先水平偏转再改变仰角；精度仍单独控制散布。',
     '联系距离设置约束参考点与施法源之间的距离，0.5—256格，仍受射程和服务器上限约束。没有该参数时维持锁定时的距离。施力、约束的效果强度也可用小数设置。'],
    ['Angle sets a fan spell\'s full aperture from 1 to 180 degrees, default 70. Fractional degrees are supported. The same aperture drives authoritative membership, block-footprint cost and visual effects. It changes neither radius nor range. Initially it applies only to Fan.',
     'Yaw changes the horizontal casting direction by -180 to 180 degrees; positive turns right. Pitch ranges from -90 to 90 degrees; positive aims up. Yaw is applied before pitch. Precision remains a separate scatter parameter.',
     'Link Distance sets the constraint reference point 0.5 to 256 blocks from the source, bounded by range and server policy. Without it, the original locking distance is retained. Force and Constraint strengths also accept decimals.'], []))

for entry in ENTRIES:
    if entry[0] == 'boards':
        entry[5].append('木、黄铜、水晶基板默认承载128、512、1280点魔力，达到四倍承载量会熔毁。存储和在途魔力也计入，超载会加速磨损。损坏后回法阵工作台的基板页，用一张同材质基板修复，原设计保留。')
        entry[6].append('Wood, brass and crystal safely hold 128, 512 and 1280 mana. Four times that load causes meltdown. Stored and travelling mana count; overload accelerates wear. Repair at the inscription table with a matching blank board. Circuit designs remain.')
    if entry[0] == 'parts':
        entry[5].append('近战只磨损杖体；施法主要磨损当前法阵所在的基板，并累积少量杖体磨损。基板耗尽只会断供，法杖不消失，也仍可近战。书阵磨损书内基板，杖阵磨损杖内基板。')
        entry[6].append('Melee wears only the staff. Casting wears the active circuit board plus a small fractional amount of staff durability. An exhausted board stops supply without destroying the wand or preventing melee. Book casts wear their book board, not a board inside the wand.')

ENTRIES.append(('routing_and_observation', 'runes', 'brass_plate', '分流、延时与探知', 'Routing, delay and observation',
    ['元素筛分把选定元素与极性送往出口1，其余送往出口2。每个出口内有多条连线时均分，来源与到达顺序保留。缺少某类成分的出口会阻塞，不会抹去魔力。信号择路则按低电平走出口1、高电平走出口2；无有效信号按低电平。',
     '选中符文，在设置栏点击具名出口开始布线；也可用顶部的出口编号选择。魔力存储另有三个信号出口：1已满、2已达到阈值且允许释放、3已空。每个出口独立连线，按观察时刻的存量判断。',
     '信号延时把每次电平变化延后指定逻辑步，保留先后与脉冲宽度。每步0.05秒，默认10步；支持1—65536步，服务器可降低新编辑的预算。停阵冻结队列，重启继续，复位清空；保存包含尚未到期的变化。',
     '探知只产生信号：0瞄准方块存在、1瞄准容器已满、2本阵有联系、3本阵无联系。容器已满指每格均占用且达到该格与物品的堆叠上限。默认读取16格，probe.range可配置；不加载区块，不取放物品。联系状态按前一世界边界观察。'],
    ['Element Filter sends the chosen element and polarity to outlet 1 and the rest to outlet 2. Lines sharing an outlet divide its mana evenly. Provenance and arrival order survive. An unconnected nonempty outlet blocks instead of destroying its mana. Signal Router selects outlet 1 for low or invalid input, outlet 2 for high.',
     'Select a rune and click its named outlet to start wiring, or choose an outlet number on the toolbar. Storage has three separate signal outlets: 1 full, 2 at threshold with release enabled, 3 empty. Each reflects the stored amount at sampling time.',
     'Signal Delay postpones each transition by a fixed number of logical ticks, preserving order and pulse width. One tick is 0.05 seconds. The default is 10, with a format ceiling of 65536; servers can lower admission budgets. Stop freezes the queue, restart resumes it, reset clears it. Saving retains pending transitions.',
     'Probe emits a read-only signal: 0 aimed block exists, 1 aimed inventory full, 2 this circuit has a link, 3 it has no link. Full means every slot is occupied to both slot and item stack limits. Default range is 16, configurable with probe.range. No chunk loading or inventory operation occurs. Link state is observed at the preceding world boundary.'], []))

ENTRIES.append(('anchoring', 'runes', 'circuit_wand', '锚定与目标筛选', 'Anchors and target selection',
    ['世界锚定在联系建立时固定参考点，配合约束可让物体悬停，不再随视线移动。联系距离可设置最初的参考位置；转动视角不会重设它，断开后重建才更新。锚定本身不施力，仍须持续供魔，并接受遮挡与射程限制。',
     '目标筛选限制新联系：0任意、1掉落物、2生物、3方块。非目标实体不会抢占联系，方块仍阻挡射线。筛选不增加拾取、伤害或容器操作权限。',
     '联系符文接受解除信号。高电平解除经过此联系符文的输出，并阻止重建；低电平后才能再次建立。它不会停止同一基板的其他联系，持续高电平也不会反复释放和重锁。'],
    ['World Anchor freezes the reference point when a link forms. With Constraint it can suspend an object independently of aim. Link Distance sets the initial reference distance. Turning does not reset it; releasing and recreating the link does. Anchor alone exerts no force. Supply, visibility and range still apply.',
     'Target Selection restricts new links: 0 any, 1 dropped items, 2 living entities, 3 blocks. Other entities cannot steal the link; blocks still obstruct the ray. Filtering grants no pickup, damage or inventory access.',
     'Connection accepts a release signal. High releases outputs passing through that connection rune and prevents re-establishment. Low permits linking again. Other connections on the board remain active, and held high never repeatedly releases and reacquires.'], []))

def build():
    book = {'name':'item.elementalcircuits.circuit_guide','landing_text':'guide.elementalcircuits.landing',
            'subtitle':'guide.elementalcircuits.subtitle','version':0,'use_resource_pack':True,
            'book_texture':'patchouli:textures/gui/book_blue.png','model':NS+'circuit_guide',
            'custom_book_item':NS+'circuit_guide','dont_generate_book':True,'show_progress':False,
            'use_blocky_font':True,'i18n':True,'text_color':'332d28','header_color':'243b50',
            'nameplate_color':'d2ac64','link_color':'245f80','link_hover_color':'a07832',
            'pause_game':False,'show_toasts':False}
    write(RES/'data/elementalcircuits/patchouli_books/field_guide/book.json',book)
    write(RES/'data/elementalcircuits/recipe/circuit_guide.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':'minecraft:book'},{'item':'minecraft:blue_dye'},{'item':'minecraft:gold_nugget'}],'result':{'id':NS+'circuit_guide','count':1}})
    for lang in ['zh_cn','en_us']:
        chinese=lang=='zh_cn'; fallback=[]
        base=RES/f'assets/elementalcircuits/patchouli_books/field_guide/{lang}'
        for sort,(id,zh,en,icon) in enumerate(CATEGORIES):
            write(base/f'categories/{id}.json',{'name':zh if chinese else en,'description':('按条目查阅，配方可直接翻看。' if chinese else 'Browse entries and their crafting recipes.'),'icon':NS+icon,'sortnum':sort})
        for sort,(id,category,icon,zh,en,zht,ent,recipes) in enumerate(ENTRIES):
            title=zh if chinese else en;texts=zht if chinese else ent
            pages=[{'type':'patchouli:text','title':title if i==0 else '', 'text':t} for i,t in enumerate(texts)]
            for i in range(0,len(recipes),2):
                page={'type':'patchouli:crafting','recipe':NS+recipes[i]}
                if i+1<len(recipes):page['recipe2']=NS+recipes[i+1]
                pages.append(page)
            write(base/f'entries/{category}/{id}.json',{'name':title,'category':NS+category,'icon':icon if ':' in icon else NS+icon,'sortnum':sort,'pages':pages})
            plain=list(texts)
            for recipe in recipes:
                data=json.loads((RES/f'data/elementalcircuits/recipe/{recipe}.json').read_text(encoding='utf-8'))
                ingredients=list(data.get('key',{}).values()) if 'key' in data else data.get('ingredients',[])
                names=[]
                translations=json.loads((RES/f'assets/elementalcircuits/lang/{lang}.json').read_text(encoding='utf-8'))
                for ingredient in ingredients:
                    name=ingredient.get('item',ingredient.get('tag',''))
                    namespace,path=name.split(':',1)
                    vanilla_zh=dict(zip(['amethyst_shard','apple','blue_dye','book','copper_ingot','crafting_table','glass','gold_nugget','iron_ingot','leather','paper','quartz','redstone','spruce_planks','stick','stone'],['紫水晶碎片','苹果','蓝色染料','书','铜锭','工作台','玻璃','金粒','铁锭','皮革','纸','下界石英','红石粉','云杉木板','木棍','石头']))
                    label=translations.get('item.'+namespace+'.'+path,translations.get('block.'+namespace+'.'+path,vanilla_zh.get(path,path.replace('_',' ')) if chinese else path.replace('_',' ')))
                    names.append(label)
                plain.append(('合成材料：' if chinese else 'Crafting materials: ')+', '.join(names)+('。安装Patchouli后可在本条目查看完整配方格。' if chinese else '. Install Patchouli to view the full crafting grid in this entry.'))
            fallback.append({'title':title,'pages':plain})
        write(RES/f'assets/elementalcircuits/guidebook/{lang}.json',fallback)
    recipe={'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':'minecraft:book'},{'item':'minecraft:blue_dye'},{'item':'minecraft:gold_nugget'}],'result':{'id':NS+'circuit_guide','count':1}}
    write(RES/'data/elementalcircuits/recipe/circuit_guide.json',recipe)
    write(RES/'data/elementalcircuits/advancement/recipes/circuit_guide.json',{'parent':'minecraft:recipes/root','criteria':{'has_book':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:book']}]}}},'rewards':{'recipes':[NS+'circuit_guide']}})
    flat={'parent':'minecraft:item/generated','textures':{'layer0':NS+'item/circuit_guide'}}
    write(RES/'assets/elementalcircuits/models/item/guide/flat.json',flat)
    write(RES/'assets/elementalcircuits/models/item/circuit_guide.json',{'loader':'neoforge:separate_transforms',
        'gui_light':'front','textures':{'particle':NS+'item/circuit_guide'},'base':{'parent':NS+'item/guide/flat'},
        'perspectives':{hand:{'parent':NS+'item/guide/held'} for hand in ['firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand']}})

def icon():
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
    # Native-style diagonal book silhouette, with a visible stepped page block.
    d.polygon([(2,5),(9,1),(14,5),(14,11),(7,15),(2,11)],fill='#111a2b')
    d.polygon([(3,6),(9,3),(13,6),(13,10),(7,13),(3,10)],fill='#c8bea4')
    d.line([(4,8),(8,10),(12,8)],fill='#eee5c7',width=1)
    d.line([(4,10),(8,12),(12,10)],fill='#8e8676',width=1)
    d.polygon([(2,5),(9,1),(14,5),(7,9)],fill='#1b304e')
    d.line([(3,5),(9,2),(13,5)],fill='#345271',width=1)
    d.polygon([(2,5),(7,9),(7,13),(2,10)],fill='#142239')
    d.line([(2,5),(7,9)],fill='#a78342',width=1)
    for p in [(3,5),(9,2),(12,5),(7,8)]:d.point(p,fill='#dec183')
    d.polygon([(8,3),(10,4),(8,6),(6,5)],fill='#327f9e')
    d.point((8,4),fill='#8fdfec');d.point((7,5),fill='#53b5d0')
    d.line([(3,8),(5,10)],fill='#b39355',width=1)
    source=ROOT/'art/aseprite/guidebook';source.mkdir(parents=True,exist_ok=True)
    im.save(source/'circuit_guide.png')
    target=RES/'assets/elementalcircuits/textures/item/circuit_guide.png';target.parent.mkdir(parents=True,exist_ok=True);im.save(target)
    im.resize((256,256),Image.Resampling.NEAREST).save(source/'cover-preview.png')

if __name__=='__main__':
    # These names must exist before generating native-reader recipe descriptions.
    for lang in ['zh_cn','en_us']:
        p=RES/f'assets/elementalcircuits/lang/{lang}.json';d=json.loads(p.read_text(encoding='utf-8'))
        zh=lang=='zh_cn';d.update({'item.elementalcircuits.circuit_guide':'法阵手札' if zh else 'Circuit Field Guide',
          'guide.elementalcircuits.hint':'从第一张法阵到节点供能' if zh else 'From your first circuit to node-powered workings',
          'guide.elementalcircuits.contents':'目录' if zh else 'Contents',
          'guide.elementalcircuits.subtitle':'刻印、装配与魔力流转' if zh else 'Inscription, assembly and mana flow',
          'guide.elementalcircuits.landing':'从一张木质基板开始，把魔力引向你需要的地方。随身携带，遇到问题时翻一翻。' if zh else 'Begin with a wooden board and guide mana where you need it. Keep these notes close when a working needs another look.'})
        write(p,d)
    build();icon()
    print(f'Guide built: {len(CATEGORIES)} categories, {len(ENTRIES)} entries, two languages.')
