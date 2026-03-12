from abc import ABC, abstractmethod
from typing import Optional
from .models import GameState, GameAction

class Agent(ABC):
    @abstractmethod
    def choose_action(self, state: GameState) -> GameAction:
        pass
