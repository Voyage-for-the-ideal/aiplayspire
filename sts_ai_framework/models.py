from typing import List, Optional, Dict, Any
from pydantic import BaseModel, ConfigDict, Field
from enum import Enum


class Card(BaseModel):
    index: int
    uuid: str
    id: str
    name: str
    cost: int
    cost_for_turn: int
    type: str = "UNKNOWN"
    target: str = "UNKNOWN"
    is_playable: bool = True
    description: Optional[str] = None
    rarity: str = "UNKNOWN"
    upgrades: int = 0
    is_bottled: bool = False
    can_upgrade: bool = True

class PlayerState(BaseModel):
    current_hp: int
    max_hp: int
    block: int
    energy: int
    gold: int

class MonsterState(BaseModel):
    name: str
    id: str
    current_hp: int
    max_hp: int
    block: int
    intent: str
    index: Optional[int] = None # Filled by client
    move: Optional["MonsterMoveState"] = None


class MonsterMoveState(BaseModel):
    damage: Optional[int] = None
    hits: Optional[int] = None

class PotionState(BaseModel):
    index: int
    id: str
    name: str
    is_empty: bool = False
    can_use: bool = False
    can_discard: bool = False
    requires_target: bool = False

class RelicState(BaseModel):
    id: str
    name: str
    counter: int = -1

class MapEdgeState(BaseModel):
    x: int
    y: int
    winged: bool = False


class MapNodeState(BaseModel):
    x: int
    y: int
    symbol: str = "?"
    lane_index_from_left: int = -1
    human_label: str = ""
    is_current: bool = False
    children: List[MapEdgeState] = []


class MapChoiceState(BaseModel):
    choice_index: int
    x: int
    y: int
    symbol: str = "?"
    lane_index_from_left: int = -1
    human_label: str = ""
    winged: bool = False


class CurrentMapNodeState(BaseModel):
    x: int
    y: int
    symbol: str = "?"
    lane_index_from_left: int = -1
    human_label: str = ""


class MapPositionState(BaseModel):
    floor: int
    lane_index_from_left: int
    symbol: str = "?"
    human_label: str = ""


class EventEffect(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: str
    amount: Optional[int] = None
    count: Optional[int] = None
    card_id: Optional[str] = None
    card_ids: List[str] = Field(default_factory=list)
    relic_id: Optional[str] = None
    purpose: Optional[str] = None
    pool: Optional[str] = None
    encounter: Optional[str] = None
    id: Optional[str] = None
    result: Optional[str] = None
    percent: Optional[int] = None
    min: Optional[int] = None
    max: Optional[int] = None
    pattern: Optional[str] = None
    exclude_ids: List[str] = Field(default_factory=list)
    unavoidable_hp_loss: Optional[int] = None
    slot: Optional[int] = None
    revealed_card_id: Optional[str] = None


class EventOutcome(BaseModel):
    model_config = ConfigDict(extra="forbid")

    probability: Optional[float] = None
    effects: List[EventEffect] = Field(default_factory=list)


class EventChoiceState(BaseModel):
    model_config = ConfigDict(extra="forbid")

    button_index: int
    action_index: Optional[int] = None
    enabled: bool
    label: str
    kind: str = "UNKNOWN"
    outcomes: List[EventOutcome] = Field(default_factory=list)
    followup: str = "NONE"


class EventState(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: str
    class_name: str
    phase: str
    semantics_status: str
    decision_kind: str
    choices: List[EventChoiceState] = Field(default_factory=list)

class GameState(BaseModel):
    player: PlayerState
    deck: List[Card] = []
    relics: List[RelicState] = []
    hand: List[Card]
    draw_pile: List[Card] = []
    discard_pile: List[Card] = []
    exhaust_pile: List[Card] = []
    draw_pile_size: int
    discard_pile_size: int
    exhaust_pile_size: int
    monsters: List[MonsterState]
    potions: List[PotionState] = []
    floor: int
    act: int
    room_phase: str
    first_room_chosen: bool = False
    map_ascii: str = ""
    map_position: Optional[MapPositionState] = None
    map_choices_human: List[str] = []
    map_nodes: List[MapNodeState] = []
    current_map_node: Optional[CurrentMapNodeState] = None
    current_map_choices: List[MapChoiceState] = []
    screen_type: Optional[str] = "NONE"
    choice_list: Optional[List[str]] = []
    reward_card_ids: Optional[List[str]] = []
    can_proceed: bool = False
    can_cancel: bool = False
    grid_selected_count: int = 0
    grid_num_cards: int = 1
    grid_purpose: str = ""
    next_boss: str = "UNKNOWN"
    is_end_turn_button_enabled: bool = False
    event: Optional[EventState] = None

class ActionType(str, Enum):
    PLAY = "play"
    POTION = "potion"
    END_TURN = "end_turn"
    WAIT = "wait"
    PROCEED = "proceed"
    CHOOSE = "choose"
    SKIP = "skip"
    CANCEL = "cancel"

class GameAction(BaseModel):
    type: ActionType
    card_index: Optional[int] = Field(None, description="Index of the card in hand to play (0-based)")
    potion_index: Optional[int] = Field(None, description="Index of the potion slot to use (0-based)")
    target_index: Optional[int] = Field(None, description="Index of the target monster (0-based)")
    choice_index: Optional[int] = Field(None, description="Index of the choice to make (0-based)")
    
    def to_api_payload(self) -> Dict[str, Any]:
        payload = {"type": self.type.value}
        if self.card_index is not None:
            payload["card_index"] = self.card_index
            payload["card"] = self.card_index
        if self.potion_index is not None:
            payload["potion_index"] = self.potion_index
        if self.target_index is not None:
            payload["target_index"] = self.target_index
            payload["target"] = self.target_index
        if self.choice_index is not None:
            payload["choice_index"] = self.choice_index
        return payload
