from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field
from enum import Enum

class CardType(str, Enum):
    ATTACK = "ATTACK"
    SKILL = "SKILL"
    POWER = "POWER"
    STATUS = "STATUS"
    CURSE = "CURSE"
    UNKNOWN = "UNKNOWN"

class CardTarget(str, Enum):
    ENEMY = "ENEMY"
    ALL_ENEMY = "ALL_ENEMY"
    SELF = "SELF"
    NONE = "NONE"
    ALL = "ALL"
    UNKNOWN = "UNKNOWN"

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

class PotionState(BaseModel):
    index: int
    id: str
    name: str
    is_empty: bool = False
    can_use: bool = False
    can_discard: bool = False
    requires_target: bool = False

class GameState(BaseModel):
    player: PlayerState
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
    screen_type: Optional[str] = "NONE"
    choice_list: Optional[List[str]] = []
    can_proceed: bool = False
    can_cancel: bool = False
    is_end_turn_button_enabled: bool = False

class ActionType(str, Enum):
    PLAY = "play"
    POTION = "potion"
    END_TURN = "end_turn"
    WAIT = "wait"
    PROCEED = "proceed"
    CHOOSE = "choose"
    CONFIRM = "confirm"
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
