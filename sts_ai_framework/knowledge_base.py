from typing import Dict, List, Optional, Union, Any

class KnowledgeBase:
    def __init__(self):
        # 结构化的怪物数据
        # 字段说明:
        # - name_cn: 中文名称
        # - mechanism: 特殊机制描述 (可选)
        # - logic: 行动逻辑描述，可以是列表或字典
        self.monsters_data: Dict[str, Dict[str, Any]] = {
            # --- 第一阶段 (塔底) ---
            "Cultist": {
                "name_cn": "异教徒",
                "mechanism": "无",
                "logic": [
                    "第1回合: 仪式 (获得3/4/5点仪式，每回合结束增加力量)。",
                    "后续回合: 黑暗打击 (攻击，6/10+伤害)。"
                ]
            },
            "Jaw Worm": {
                "name_cn": "大颚虫",
                "mechanism": "无",
                "logic": [
                    "在以下三个动作中循环 (不会连续使用同一动作3次):",
                    "1. 重击 (Chomp): 高伤害 (11/12)。(不会连续使用2次)",
                    "2. 横扫 (Thrash): 伤害 (7) + 格挡 (5)。",
                    "3. 吼叫 (Bellow): 获得力量 (3/4/5) + 格挡 (6/9)。(不会连续使用2次)"
                ]
            },
            "Green Louse": {
                "name_cn": "绿虱虫",
                "mechanism": "蜷缩 (受到攻击时获得格挡，每场战斗一次)",
                "logic": [
                    "随机使用:",
                    "1. 吐网: 施加虚弱 (2)。",
                    "2. 咬: 伤害 (5/6+)。"
                ]
            },
            "Red Louse": {
                "name_cn": "红虱虫",
                "mechanism": "蜷缩 (受到攻击时获得格挡，每场战斗一次)",
                "logic": [
                    "随机使用:",
                    "1. 生长: 获得力量 (3/4)。",
                    "2. 咬: 伤害 (5/6+)。"
                ]
            },
            "Acid Slime (L)": {
                "name_cn": "酸液史莱姆(大)",
                "mechanism": "分裂 (HP <= 50% 时分裂成两个中型史莱姆，当前HP继承)",
                "logic": [
                    "随机使用 (不会连续使用同一动作3次):",
                    "1. 腐蚀吐息: 伤害 (11/12) + 塞入2张粘液。",
                    "2. 舔舐: 施加虚弱 (2)。",
                    "3. 撞击: 伤害 (16/18)。(概率较低)",
                    "触发分裂: 当 HP 低于 50% 时，打断当前行动并使用'分裂'。"
                ]
            },
            "Spike Slime (L)": {
                "name_cn": "尖刺史莱姆(大)",
                "mechanism": "分裂 (HP <= 50% 时分裂成两个中型史莱姆，当前HP继承)",
                "logic": [
                    "随机使用 (不会连续使用同一动作3次):",
                    "1. 火焰撞击: 伤害 (16/18) + 塞入2张粘液。",
                    "2. 舔舐: 施加易伤 (2)。",
                    "触发分裂: 当 HP 低于 50% 时，打断当前行动并使用'分裂'。"
                ]
            },
            "Gremlin Nob": {
                "name_cn": "地精大块头 (精英)",
                "mechanism": "激怒 (每当你打出一张技能牌，获得 2/3 点力量)",
                "logic": [
                    "第1回合: 吼叫 (获得激怒状态)。",
                    "后续回合: 随机使用 (1) 猛冲 (攻击) 或 (2) 碎颅击 (攻击+易伤)。"
                ]
            },
            "Lagavulin": {
                "name_cn": "乐加维林 (精英)",
                "mechanism": "金属化 (每回合获得8格挡)，沉睡 (前3回合或是受到攻击前处于沉睡状态)",
                "logic": [
                    "沉睡状态: 昏迷 (无行动)。",
                    "苏醒后循环:",
                    "1. 攻击 (18/20)。",
                    "2. 攻击 (18/20)。",
                    "3. 灵魂吸取 (-1/-2 力量和敏捷)。"
                ]
            },
            "Sentries": {
                "name_cn": "哨卫 (精英)",
                "mechanism": "人工制品 (1层)",
                "logic": [
                    "交替行动 (从不连续做相同动作):",
                    "1. 光束: 伤害 (9/10)。",
                    "2. 螺栓: 向弃牌堆塞入 2/3 张眩晕。",
                    "(通常三个哨卫中，中间的一个先使用螺栓，两边的先使用光束，以此交错)"
                ]
            },
            "The Guardian": {
                "name_cn": "守护者 (Boss)",
                "mechanism": "形态转换 (当受到 30/40 点伤害后，转换为防御模式)",
                "logic": [
                    "攻击模式:",
                    "1. 蓄力 (获得9格挡)。",
                    "2. 猛击 (32/36 伤害)。",
                    "3. 旋风斩 (4x5 伤害) 或 喷气 (易伤)。",
                    "防御模式 (拥有20格挡+荆棘):",
                    "1. 防御模式下保持不动或轻微攻击，直到回合结束或护盾被打破后返回攻击模式。"
                ]
            },
            "Hexaghost": {
                "name_cn": "六火亡魂 (Boss)",
                "mechanism": "异界之火 (第2回合造成基于你当前HP的伤害)",
                "logic": [
                    "第1回合: 激活 (无行动/准备)。",
                    "第2回合: 分割 (伤害 = (当前HP / 12) * 6 + 1)。",
                    "后续循环:",
                    "1. 灼烧 (伤害 + 灼烧牌)。",
                    "2. 强化 (获得力量)。",
                    "3. 炼狱 (高伤害，6x)。"
                ]
            },
            "Slime Boss": {
                "name_cn": "史莱姆老大 (Boss)",
                "mechanism": "分裂 (HP <= 50% 时分裂)",
                "logic": [
                    "循环:",
                    "1. 粘液喷射 (塞入3/5张粘液)。",
                    "2. 准备 (无行动)。",
                    "3. 强力重击 (35/38 伤害)。",
                    "触发分裂: 当 HP 低于 50% 时，使用'分裂'。"
                ]
            },
            
            # --- 第二阶段 (城市) ---
            "Spheric Guardian": {
                "name_cn": "圆球守护者",
                "mechanism": "壁垒 (格挡不会在回合开始时消失)，人工制品 (3层)",
                "logic": [
                    "循环:",
                    "1. 激活 (获得 25/40 格挡)。",
                    "2. 攻击 (10/11) x2。",
                    "3. 重击 (10/11) + 易伤。"
                ]
            },
            "Chosen": {
                "name_cn": "被拣选者",
                "mechanism": "无",
                "logic": [
                    "第1回合: 戳刺 (伤害 + 邪咒:每当打出非攻击牌，将一张晕眩放入抽牌堆)。",
                    "后续回合: 随机攻击或施加易伤。"
                ]
            },
            "Bronze Automaton": {
                "name_cn": "铜制机械人偶 (Boss)",
                "mechanism": "人工制品 (3层)，召唤小怪",
                "logic": [
                    "第1回合: 召唤2个铜球。",
                    "循环:",
                    "1. 连击 (7/8 x2)。",
                    "2. 连击 (7/8 x2)。",
                    "3. 超光束 (45/58 伤害)。"
                ]
            },
            "Champ": {
                "name_cn": "第一勇士 (Boss)",
                "mechanism": "第二阶段 (HP < 50% 清除Debuff并回满力量)",
                "logic": [
                    "第一阶段: 嘲讽 / 重击 / 防御 交替。",
                    "第二阶段 (HP < 50%):",
                    "1. 愤怒 (清除Debuff，获得力量)。",
                    "2. 处决 (极高伤害)。"
                ]
            },
            
             # --- 第三阶段 (深处) ---
            "Darkling": {
                "name_cn": "小黑",
                "mechanism": "重生 (只要还有其他小黑存活，死去的小黑会在几回合后以半血复活)",
                "logic": [
                    "随机使用:",
                    "1. 啃咬 (7-11伤害)。",
                    "2. 双击 (8x2伤害)。",
                    "3. 硬化 (获得格挡)。"
                ]
            },
            "Orb Walker": {
                "name_cn": "圆球行者",
                "mechanism": "每回合获得力量",
                "logic": [
                    "循环:",
                    "1. 激光 (10/11伤害 + 灼烧)。",
                    "2. 爪击 (15/16伤害)。"
                ]
            },
            "Time Eater": {
                "name_cn": "时间吞噬者 (Boss)",
                "mechanism": "时间扭曲 (每当你打出第12张牌，强制结束你的回合并没有获得力量)",
                "logic": [
                    "随机使用:",
                    "1. 头部撞击 (7x3 伤害)。",
                    "2. 震荡波 (伤害 + 虚弱/易伤)。",
                    "3. 涟漪 (获得格挡 + 降低你的抽牌数)。",
                    "HP < 50% 时: 恢复生命至 50% 并清除 Debuff (仅一次)。"
                ]
            },
            "Awakened One": {
                "name_cn": "觉醒者 (Boss)",
                "mechanism": "好奇 (每当你打出能力牌，获得力量); 复活 (第一形态死后进入第二形态)",
                "logic": [
                    "第一形态: 带着两个异教徒。攻击 (20) / 乱舞 (6x4)。",
                    "第二形态: 失去好奇，获得更多力量。攻击 (40+) / 污泥 (伤害+黑暗)。"
                ]
            },
            "Donu": {
                "name_cn": "甜圈 (Boss)",
                "mechanism": "人工制品",
                "logic": [
                    "循环:",
                    "1. 力量之环 (全体获得力量)。",
                    "2. 攻击 (10/12 x2)。"
                ]
            },
            "Deca": {
                "name_cn": "八体 (Boss)",
                "mechanism": "人工制品",
                "logic": [
                    "循环:",
                    "1. 方块护盾 (全体获得格挡)。",
                    "2. 攻击 (10/12 x2) + 塞入2张晕眩。"
                ]
            }
        }
        
        # 简单的卡牌和意图描述保持不变或稍作优化
        self.cards: Dict[str, str] = {
            "Strike_R": "造成 6 点伤害。",
            "Defend_R": "获得 5 点格挡。",
            "Bash": "痛击: 造成 8 点伤害。施加 2 层易伤。",
            "Strike_G": "造成 6 点伤害。",
            "Defend_G": "获得 5 点格挡。",
            "Survivor": "生存者: 获得 8 点格挡。丢弃一张牌。",
            "Neutralize": "中和: 造成 3 点伤害。施加 1 层虚弱。",
            "Strike_B": "造成 6 点伤害。",
            "Defend_B": "获得 5 点格挡。",
            "Zap": "电击: 生成 1 个闪电充能球。",
            "Dualcast": "双重释放: 激发你最右边的充能球 2 次。",
            # 通用
            "AscendersBane": "进阶之灾: 无法打出。虚无。",
            "Slimed": "粘液: 消耗1能量。消耗。",
            "Dazed": "晕眩: 无法打出。虚无。",
            "Burn": "灼烧: 回合结束受到2点伤害。",
            "Wound": "伤口: 无法打出。",
        }
        
        self.intents: Dict[str, str] = {
            "ATTACK": "攻击",
            "ATTACK_BUFF": "攻击+强化",
            "ATTACK_DEBUFF": "攻击+削弱",
            "ATTACK_DEFEND": "攻击+防御",
            "BUFF": "强化自身",
            "DEBUFF": "削弱玩家",
            "STRONG_DEBUFF": "强力削弱",
            "DEBUG": "疑惑",
            "DEFEND": "防御",
            "DEFEND_BUFF": "防御+强化",
            "DEFEND_DEBUFF": "防御+削弱",
            "ESCAPE": "逃跑",
            "MAGIC": "魔法",
            "NONE": "无",
            "SLEEP": "沉睡",
            "STUN": "眩晕",
            "UNKNOWN": "未知"
        }

    def get_monster_info(self, name: str) -> str:
        data = self.monsters_data.get(name)
        if not data:
            return "未知怪物行为。"
        
        info = f"{data['name_cn']}: "
        if data.get('mechanism') and data['mechanism'] != "无":
            info += f"[机制: {data['mechanism']}] "
        
        if 'logic' in data:
            if isinstance(data['logic'], list):
                info += " ".join(data['logic'])
            else:
                info += str(data['logic'])
        
        return info

    def get_card_info(self, name: str) -> str:
        return self.cards.get(name, "未知卡牌效果。")

    def get_intent_info(self, intent_enum: str) -> str:
        return self.intents.get(intent_enum, "未知意图。")
