import collections
import re
from typing import Dict, Generator

class RunReconstructor:
    """
    时光机回放器 (Reconstructor)
    重构每一层的状态，包括卡组、遗物、金币、血量，生成按层的训练样本。
    已修复：根据房间类型（事件/战斗）动态校准 HP 和 Gold 的状态索引。
    """
    
    STARTER_DECKS = {
        'IRONCLAD': ['Strike_R']*5 + ['Defend_R']*4 + ['Bash'],
        'THE_SILENT': ['Strike_G']*5 + ['Defend_G']*5 + ['Survivor', 'Neutralize'],
        'DEFECT': ['Strike_B']*4 + ['Defend_B']*4 + ['Zap', 'Dualcast'],
        'WATCHER': ['Strike_P']*4 + ['Defend_P']*4 + ['Eruption', 'Vigilance'],
    }
    
    STARTER_RELICS = {
        'IRONCLAD': 'Burning Blood',
        'THE_SILENT': 'Ring of the Snake',
        'DEFECT': 'Cracked Core',
        'WATCHER': 'PureWater'
    }

    def __init__(self, run_data):
        self.raw_data = run_data
        self.character = self.raw_data.get('character_chosen', 'IRONCLAD')
        self.ascension = self.raw_data.get('ascension_level', 0)
        self.floor_reached = self.raw_data.get('floor_reached', 0)
        self.is_victory = self.raw_data.get('victory', False)
        self.killed_by = self.raw_data.get('killed_by', None)
        self.master_deck = self.raw_data.get('master_deck', [])
        
        self.deck = list(self.STARTER_DECKS.get(self.character, []))
        if self.ascension >= 10:
            self.deck.append('AscendersBane')
            
        self.relics = [self.STARTER_RELICS.get(self.character, '')]
        self.max_hp = self.raw_data.get('max_hp_per_floor', [70])[0] if self.raw_data.get('max_hp_per_floor') else 70
        self.hp = self.max_hp
        self.gold = 99
        self.omamori_charges = 0
        
        self._implicit_removals = collections.defaultdict(list)
        self._implicit_additions = collections.defaultdict(list)
        self._is_dry_run = False

        self._apply_neow_bonus()
        
        # 核心：跑一次完整干跑并对账
        self._reconcile_all_diffs()

    def _reconcile_all_diffs(self):
        """
        终极差分对齐机制 (Two-Pass Diff Reconciler)
        """
        backup_deck = list(self.deck)
        backup_relics = list(self.relics)
        backup_hp = self.hp
        backup_max = self.max_hp
        backup_gold = self.gold
        backup_omamori = self.omamori_charges

        self._is_dry_run = True
        for _ in self.replay():
            pass
        self._is_dry_run = False

        simulated_cnt = collections.Counter(self.deck)
        master_cnt = collections.Counter(self.master_deck)

        excess = list((simulated_cnt - master_cnt).elements())
        missing = list((master_cnt - simulated_cnt).elements())

        # === 阶段 1：找出隐形升级 ===
        i = 0
        while i < len(excess):
            c_excess = excess[i]
            base_ex, lvl_ex = self._split_upgrade_level(c_excess)
            
            matched_missing = None
            for c_miss in missing:
                base_ms, lvl_ms = self._split_upgrade_level(c_miss)
                if base_ms == base_ex and lvl_ms > lvl_ex:
                    matched_missing = c_miss
                    break
                    
            if matched_missing:
                # ====== 核心修复：根据遗物或场景，计算确切的升级楼层 ======
                # 情况 A: 蛋类遗物效应 (获得卡牌时自动升级)
                # 这类升级应该在获取卡牌时处理，这里我们将它们记录到“获得该基础牌的楼层”
                # 但更简单的方法是：如果玩家在此时（全回放后）拥有对应的蛋，那么每一张被记录在此时的
                # 升级差异都可以追溯到该牌被获得的时刻。
                
                # 情况 B: 战纹涂料 (War Paint) / 磨刀石 (Whetstone)
                # 这种是一次性的，我们需要定位玩家获得该遗物的楼层。
                relic_upgraders = {'War Paint', 'Whetstone'}
                found_relic_floor = -1
                for item in self.raw_data.get('relics_obtained', []):
                    if item.get('key') in relic_upgraders:
                        # 如果升级的是基础攻击/防御，大概率是这类遗物干的
                        if self._is_starter_card(base_ex):
                            found_relic_floor = item.get('floor', 0)
                            break
                
                up_floor = found_relic_floor if found_relic_floor != -1 else self.floor_reached
                
                # 记录这种隐性变动
                self._implicit_removals[up_floor].append(c_excess)
                self._implicit_additions[up_floor].append(matched_missing)
                
                excess.pop(i)
                missing.remove(matched_missing)
            else:
                i += 1

        # === 阶段 2：严格基于特定遗物和楼层的容量约束匹配 ===
        target_relics = {'Empty Cage', "Pandora's Box", 'Astrolabe'}
        relic_floors = {}
        
        if self.raw_data.get('relics', [''])[0] in target_relics: 
            relic_floors[self.raw_data['relics'][0]] = 0
        for boss_idx, fl in enumerate([17, 34]):
            if len(self.raw_data.get('boss_relics', [])) > boss_idx:
                r = self.raw_data['boss_relics'][boss_idx].get('picked')
                if r in target_relics: relic_floors[r] = fl
        for item in self.raw_data.get('relics_obtained', []):
            if item.get('key') in target_relics: 
                relic_floors[item.get('key')] = item.get('floor', 0)

        for r_name, fl in sorted(relic_floors.items(), key=lambda x: x[1]):
            if r_name == 'Empty Cage':
                for _ in range(2):
                    if excess: self._implicit_removals[fl].append(excess.pop(0))
            elif r_name == 'Astrolabe':
                for _ in range(3):
                    if excess: self._implicit_removals[fl].append(excess.pop(0))
                    if missing: self._implicit_additions[fl].append(missing.pop(0))
            elif r_name == "Pandora's Box":
                p_rems = [c for c in excess if self._is_starter_strike_or_defend(c)]
                for c in p_rems: excess.remove(c)
                self._implicit_removals[fl].extend(p_rems)
                for _ in range(len(p_rems)):
                    if missing: self._implicit_additions[fl].append(missing.pop(0))

        # === 阶段 3: Neow 黑盒的卡牌变动约束 ===
        neow_bonus = self.raw_data.get('neow_bonus', '')
        neow_cost = self.raw_data.get('neow_cost', '')

        # 处理 neow_cost 带来的隐形变动
        if neow_cost == 'CURSE':
            # 随机诅咒通常在第 0 层获得，且目前就在 missing 池中
            if missing: self._implicit_additions[0].append(missing.pop())

        # 逆序提取 missing 中最靠后的牌，因为它们是游戏中最早获得的（被压在 master_deck 栈底）
        if neow_bonus in {'TRANSFORM_TWO_CARDS', 'REMOVE_TWO', 'REMOVE_TWO_CARDS'}:
            for _ in range(2):
                if excess: self._implicit_removals[0].append(excess.pop(0))
            if 'TRANSFORM' in neow_bonus:
                for _ in range(2):
                    if missing: self._implicit_additions[0].append(missing.pop())
        elif neow_bonus in {'TRANSFORM_CARD', 'REMOVE_CARD'}:
            if excess: self._implicit_removals[0].append(excess.pop(0))
            if 'TRANSFORM' in neow_bonus:
                if missing: self._implicit_additions[0].append(missing.pop())
        elif neow_bonus == 'ONE_RANDOM_RARE_CARD':
            if missing: self._implicit_additions[0].append(missing.pop())
        elif neow_bonus in {'RANDOM_COLORLESS', 'RANDOM_COLORLESS_2', 'THREE_CARDS', 'THREE_RARE_CARDS'}:
            # 这些奖励都会开启选牌界面，通常表现为在第 0 层获得了一张新卡
            if missing: self._implicit_additions[0].append(missing.pop())
        elif neow_bonus == 'UPGRADE_CARD':
            # 特殊处理 Neow 升级：将其从最后一层迁回第 0 层
            if self._implicit_removals[self.floor_reached] and self._implicit_additions[self.floor_reached]:
                # 找到基础名相同的对子
                found = False
                for i, rem in enumerate(self._implicit_removals[self.floor_reached]):
                    base_rem, _ = self._split_upgrade_level(rem)
                    for j, add in enumerate(self._implicit_additions[self.floor_reached]):
                        base_add, _ = self._split_upgrade_level(add)
                        if base_rem == base_add and self._is_starter_card(base_rem):
                            self._implicit_removals[0].append(self._implicit_removals[self.floor_reached].pop(i))
                            self._implicit_additions[0].append(self._implicit_additions[self.floor_reached].pop(j))
                            found = True
                            break
                    if found: break

        # 恢复状态
        self.deck = backup_deck
        self.relics = backup_relics
        self.hp = backup_hp
        self.max_hp = backup_max
        self.gold = backup_gold
        self.omamori_charges = backup_omamori

        for c in self._implicit_removals.get(0, []):
            self._remove_card(c)
        for c in self._implicit_additions.get(0, []):
            self.deck.append(c)

    def _is_starter_strike_or_defend(self, card_name: str) -> bool:
        base_name, _ = self._split_upgrade_level(card_name)
        return base_name.startswith('Strike') or base_name.startswith('Defend')

    def _add_relic(self, relic):
        if relic and relic not in self.relics:
            self.relics.append(relic)
            if relic == 'Omamori':
                self.omamori_charges += 2
            elif relic == 'Calling Bell':
                if self.omamori_charges > 0:
                    self.omamori_charges -= 1
                else:
                    self.deck.append('CurseOfTheBell')
            elif relic == 'War Paint':
                # 随机升级 2 张技能牌
                self._handle_random_relic_upgrade(target_type='SKILL', count=2)
            elif relic == 'Whetstone':
                # 随机升级 2 张攻击牌
                self._handle_random_relic_upgrade(target_type='ATTACK', count=2)

    def _handle_random_relic_upgrade(self, target_type, count):
        """
        处理战纹涂料（War Paint）或磨刀石（Whetstone）的隐性升级。
        由于是随机升级，具体升级了哪两张牌，我们必须与 master_deck 的终态进行交叉验证。
        """
        # 我们不能真正“随机”选择，必须找到 master_deck 中哪些牌被升级了，
        # 且这些牌目前在我们的 deck 中还是基础形态。
        potential_upgrades = []
        
        sim_cnt = collections.Counter(self.deck)
        master_cnt = collections.Counter(self.master_deck)
        
        # 找出 master_deck 里是升级版，但此时 deck 里还是基础版的牌（且符合类型要求）
        # 这里的 diff 记录的是最终差值。如果一张牌被 War Paint 升级了，
        # 那么它在此时（获取遗物那一层）的基础形态就会少一张，升级形态就会多一张。
        
        # 虽然我们不知道具体的卡牌类型（因为没有外部库），
        # 但我们可以通过“此时基础牌在 excess 中，升级牌在 missing 中”来锁定此时最合理的升级目标。
        
        # 获取当前的差分对账结果（基于最终卡组）
        # 注意：这需要 _reconcile_all_diffs 已经运行过，并且我们将一些升级记录到了 _implicit 列表
        pass

    def _is_starter_card(self, card_name: str) -> bool:
        base_name, _ = self._split_upgrade_level(card_name)
        starter_bases = {self._split_upgrade_level(c)[0] for c in self.STARTER_DECKS.get(self.character, [])}
        return base_name in starter_bases

    def _apply_neow_bonus(self):
        bonus = self.raw_data.get('neow_bonus')
        if not bonus:
            return

        floor0_events = [e for e in self.raw_data.get('event_choices', []) if e.get('floor') == 0]
        floor0_purges = [
            self.raw_data.get('items_purged', [])[i]
            for i, f in enumerate(self.raw_data.get('items_purged_floors', []))
            if f == 0 and i < len(self.raw_data.get('items_purged', []))
        ]

        for card in floor0_purges:
            self._remove_card(card)

        for ev in floor0_events:
            for c in ev.get('cards_removed', []):
                self._remove_card(c)
            for c in ev.get('cards_upgraded', []):
                self._upgrade_card(c)
            for c in ev.get('cards_transformed', []):
                self._remove_card(c)
            for c in ev.get('cards_obtained', []):
                self.deck.append(c)

        if bonus == 'THREE_ENEMY_KILL':
            self._add_relic('NeowsBlessing')
        elif bonus in ['ONE_RANDOM_RARE_RELIC', 'RANDOM_COMMON_RELIC']:
            for relic in self.raw_data.get('relics', []):
                if relic and relic != self.relics[0] and relic not in self.relics:
                    self._add_relic(relic)
                    break
        elif bonus == 'BOSS_RELIC':
            if self.relics and self.raw_data.get('relics'):
                if 'Black Blood' not in self.raw_data['relics'] and 'Ring of the Serpent' not in self.raw_data['relics']:
                    starter = self.relics[0]
                    if starter in self.relics:
                        self.relics.remove(starter)
                    self._add_relic(self.raw_data['relics'][0])

    def _split_upgrade_level(self, card_name):
        if not isinstance(card_name, str) or not card_name:
            return '', 0
        m = re.match(r'^(.*?)(?:\+(\d+))?$', card_name)
        if not m:
            return card_name, 0
        return m.group(1), int(m.group(2)) if m.group(2) else 0

    def _format_card_name(self, base_name, level):
        return base_name if level <= 0 else f'{base_name}+{level}'

    def _resolve_card_choices_by_floor(self):
        card_choices = self.raw_data.get('card_choices', [])
        grouped = collections.defaultdict(list)
        for idx, item in enumerate(card_choices):
            if isinstance(item, dict) and 'floor' in item:
                grouped[item['floor']].append((idx, item))

        resolved = {}
        for floor, entries in grouped.items():
            last_by_pool = {}
            for idx, choice in entries:
                not_picked = choice.get('not_picked', []) or []
                picked = choice.get('picked')

                pool_cards = list(not_picked)
                if picked and picked not in {'SKIP', 'Singing Bowl'}:
                    pool_cards.append(picked)
                pool_key = tuple(sorted(pool_cards))
                last_by_pool[pool_key] = (idx, choice)

            resolved_choices = [x[1] for x in sorted(last_by_pool.values(), key=lambda x: x[0])]
            resolved[floor] = resolved_choices

        return resolved

    def _remove_card(self, card_name):
        if card_name in self.deck:
            self.deck.remove(card_name)
            return
            
        base_name = card_name.replace('+1', '').replace('+2', '').strip('+')
        
        for i, c in enumerate(self.deck):
            if c == base_name:
                self.deck.pop(i)
                return
                
        for i, c in enumerate(self.deck):
            c_base = c.replace('+1', '').replace('+2', '').strip('+')
            if c_base == base_name:
                self.deck.pop(i)
                return

    def _upgrade_card(self, card_name):
            if not card_name:
                return

            base_target, _ = self._split_upgrade_level(card_name)

            if card_name in self.deck:
                idx = self.deck.index(card_name)
                base_name, level = self._split_upgrade_level(self.deck[idx])
                if base_name != 'Searing Blow' and level >= 1:
                    return
                self.deck[idx] = self._format_card_name(base_name, level + 1)
                return

            candidates = []
            for i, c in enumerate(self.deck):
                c_base, c_level = self._split_upgrade_level(c)
                if c_base == base_target:
                    candidates.append((c_level, i))

            if candidates:
                c_level, idx = min(candidates) 
                c_base, _ = self._split_upgrade_level(self.deck[idx])
                if c_base != 'Searing Blow' and c_level >= 1:
                    return
                self.deck[idx] = self._format_card_name(c_base, c_level + 1)

    def _has_shop_visit(self) -> bool:
        path_per_floor = self.raw_data.get('path_per_floor', [])
        path_taken = self.raw_data.get('path_taken', [])
        shop_markers = {'$', 'SHOP'}

        return any(node in shop_markers for node in path_per_floor) or any(
            node in shop_markers for node in path_taken
        )
        
    def validate_run(self) -> bool:
        if self.ascension < 15:
            return False
        if not self.is_victory and not self.killed_by:
            return False
        if 'PrismaticShard' in self.raw_data.get('relics', []):
            return False
        if self.character not in {'IRONCLAD', 'THE_SILENT', 'DEFECT', 'WATCHER'}:
            return False
        if not self.raw_data.get('character_chosen') or self.floor_reached <= 0:
            return False
            
        deck_size = len(self.deck)
        master_deck_size = len(self.master_deck) if self.master_deck else 0
        if master_deck_size > 0 and abs(deck_size - master_deck_size) > 10:
            return False
        if master_deck_size > 0 and abs(deck_size - master_deck_size) > 0 and self._has_shop_visit():
            return False
        return True
        
    def replay(self) -> Generator[Dict, None, None]:
        purchases = {}
        for i, floor in enumerate(self.raw_data.get('item_purchase_floors', [])):
            if i < len(self.raw_data.get('items_purchased', [])):
                purchases.setdefault(floor, []).append(self.raw_data['items_purchased'][i])
                
        card_choices = self._resolve_card_choices_by_floor()
        relics_obtained = {}
        for item in self.raw_data.get('relics_obtained', []):
            if 'floor' in item:
                relics_obtained.setdefault(item['floor'], []).append(item.get('key', ''))
            
        campfires = {}
        for item in self.raw_data.get('campfire_choices', []):
            if 'floor' in item:
                campfires.setdefault(item['floor'], []).append(item)
            
        events = {}
        for item in self.raw_data.get('event_choices', []):
            if 'floor' in item:
                events.setdefault(item['floor'], []).append(item)
            
        purges = {}
        for i, floor in enumerate(self.raw_data.get('items_purged_floors', [])):
            if i < len(self.raw_data.get('items_purged', [])):
                purges.setdefault(floor, []).append(self.raw_data['items_purged'][i])
                
        # 提前获取路径信息
        path_per_floor = self.raw_data.get('path_per_floor', [])

        for floor in range(0, self.floor_reached + 1):
            if floor > 0:
                # 状态的基础跟随逻辑：维护 Reconstructor 的当前值
                if floor >= 2:
                    prev_idx = floor - 2
                    if prev_idx < len(self.raw_data.get('current_hp_per_floor', [])):
                        self.hp = self.raw_data['current_hp_per_floor'][prev_idx]
                    if prev_idx < len(self.raw_data.get('max_hp_per_floor', [])):
                        self.max_hp = self.raw_data['max_hp_per_floor'][prev_idx]
                    if prev_idx < len(self.raw_data.get('gold_per_floor', [])):
                        self.gold = self.raw_data['gold_per_floor'][prev_idx]
            
            if floor == 17 and len(self.raw_data.get('boss_relics', [])) > 0:
                boss_relic = self.raw_data['boss_relics'][0].get('picked')
                if boss_relic:
                    self._add_relic(boss_relic)
            if floor == 34 and len(self.raw_data.get('boss_relics', [])) > 1:
                boss_relic = self.raw_data['boss_relics'][1].get('picked')
                if boss_relic:
                    self._add_relic(boss_relic)

            if floor in relics_obtained:
                for r in relics_obtained[floor]:
                    self._add_relic(r)

            if floor in purchases:
                # 商店逻辑：先拿遗物，再拿卡牌，最后处理移除
                item_list = purchases[floor]
                all_relics = self.raw_data.get('relics', [])
                
                # 1. 先买遗物
                for item in item_list:
                    if item in all_relics and item not in self.relics:
                        self._add_relic(item)
                
                # 2. 再买卡牌
                for item in item_list:
                    if item not in all_relics:
                        self.deck.append(item)
                        # 触发蛋类遗物对商店购买的即时升级
                        self._handle_egg_upgrade(item)
                            
            if floor in purges:
                for prg in purges[floor]:
                    self._remove_card(prg)
                    
            if floor in campfires:
                for cf in campfires[floor]:
                    if cf.get('key') == 'SMITH':
                        self._upgrade_card(cf.get('data'))
                    elif cf.get('key') == 'PURGE':
                        self._remove_card(cf.get('data'))
                        
            if floor in events:
                for ev in events[floor]:
                    # 事件逻辑：先拿遗物，再处理卡牌相关
                    for relic in ev.get('relics_obtained', []):
                        self._add_relic(relic)
                    
                    for c in ev.get('cards_obtained', []):
                        self.deck.append(c)
                        self._handle_egg_upgrade(c)
                    
                    for c in ev.get('cards_removed', []):
                        self._remove_card(c)
                    for c in ev.get('cards_upgraded', []):
                        self._upgrade_card(c)
                    for c in ev.get('cards_transformed', []):
                        self._remove_card(c)
                        
                    if ev.get('event_name') == 'Vampires' and any(c.startswith('Bite') for c in ev.get('cards_obtained', [])):
                        self.deck = [c for c in self.deck if not c.startswith('Strike')]
                    
                    for relic in ev.get('relics_lost', []):
                        if relic in self.relics:
                            self.relics.remove(relic)

            if not getattr(self, '_is_dry_run', False) and floor > 0:
                for c in self._implicit_removals.get(floor, []):
                    self._remove_card(c)
                for c in self._implicit_additions.get(floor, []):
                    self.deck.append(c)
                    self._handle_egg_upgrade(c)

            if floor in card_choices:
                # 战利品选牌逻辑：已经在最上方先拿了 relics_obtained，所以这里直接判定
                # ====== 核心修复：根据房间类型决定索引 ======
                # path_per_floor 记录的是每层的节点类型，索引为 floor-1
                is_event_room = False
                if 0 <= floor - 1 < len(path_per_floor):
                    is_event_room = (path_per_floor[floor - 1] == '?')

                # 逻辑：事件选牌取 floor-2（入场前），战斗选牌取 floor-1（战斗结算后）
                target_state_idx = floor - 2 if is_event_room else floor - 1
                
                # 默认值
                y_hp, y_max, y_gold = self.hp, self.max_hp, self.gold

                if target_state_idx >= 0:
                    curr_hp_list = self.raw_data.get('current_hp_per_floor', [])
                    max_hp_list = self.raw_data.get('max_hp_per_floor', [])
                    gold_list = self.raw_data.get('gold_per_floor', [])
                    
                    if target_state_idx < len(curr_hp_list):
                        y_hp = curr_hp_list[target_state_idx]
                    if target_state_idx < len(max_hp_list):
                        y_max = max_hp_list[target_state_idx]
                    if target_state_idx < len(gold_list):
                        y_gold = gold_list[target_state_idx]
                # ==========================================

                for choice in card_choices[floor]:
                    if not getattr(self, '_is_dry_run', False):
                        yield {
                            'floor': floor,
                            'deck': list(self.deck),
                            'relics': list(self.relics),
                            'hp': y_hp,
                            'max_hp': y_max,
                            'gold': y_gold,
                            'ascension': self.ascension,
                            'candidates': (choice.get('not_picked', []) or []) + [choice.get('picked')],
                            'picked': choice.get('picked')
                        }
                    picked = choice.get('picked')
                    if picked and picked not in {'SKIP', 'Singing Bowl'}:
                        self.deck.append(picked)
                        self._handle_egg_upgrade(picked)

    def _handle_egg_upgrade(self, card_name: str):
        """
        蛋类遗物的自动升级逻辑。这些遗物在该卡牌被加入卡组的一瞬间触发。
        """
        if not card_name: return
        base_name, level = self._split_upgrade_level(card_name)
        if level > 0: return # 已经是升级的牌不再处理

        # 找到卡组中最后一张匹配的基础卡（刚刚加进去的那张）
        idx = -1
        for i in range(len(self.deck)-1, -1, -1):
            if self.deck[i] == card_name:
                idx = i
                break
        
        if idx == -1: return

        # 检查蛋类遗物
        # TODO: 这里需要卡牌类型的知识，暂时通过特征字符串或逻辑占位
        # Molten Egg: 攻击牌 | Frozen Egg: 能力牌 | Toxic Egg: 技能牌
        # 我们这里使用 _upgrade_card 逻辑，它会尝试升级这张牌
        if 'Molten Egg' in self.relics:
            # 简单启发式：如果 master_deck 里这张牌是升级的，且我们有对应的蛋，就升级它
            # 或者更严谨地检查卡牌数据库（暂不可用）
            pass
        
        # 考虑到目前的架构，最稳妥的方法是：如果在拿到该牌时，我们已经拥有对应的蛋
        # 且在 master_deck 中这张牌确实是升级的，那么这里就应该升级。
        # 但既然我们现在在 replay 流程中，如果我们在此时已经拥有蛋，逻辑上就应该自动升级。
        # 实际操作中，为了兼容性，我们直接复用 _reconcile_all_diffs 产生的 implicit 升级记录更稳妥。
        # 这里的显式调用仅作为第一道防线。

    def is_match_with_master_deck(self) -> bool:
        """
        验证最终重构的卡组是否与 master_deck 完美匹配。
        如果存在无法解决的差分（excess/missing），说明重放逻辑不可靠，相关样本应抛弃。
        """
        if not self.master_deck:
            return True
        return collections.Counter(self.deck) == collections.Counter(self.master_deck)