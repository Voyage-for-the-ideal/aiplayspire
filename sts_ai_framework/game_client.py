import requests
import json
from typing import Optional, List, Tuple
from .models import GameState, GameAction, ActionType
import time

class GameClient:
    def __init__(self, base_url: str = "http://localhost:5000"):
        self.base_url = base_url
        self.state_url = f"{base_url}/state"
        self.action_url = f"{base_url}/action"

    def get_state(self) -> Optional[GameState]:
        try:
            response = requests.get(self.state_url, timeout=2.0)
            if response.status_code == 200:
                try:
                    data = response.json()
                except json.JSONDecodeError:
                    print("Error: Invalid JSON response")
                    return None

                # Check for mod error messages
                if "error" in data:
                    # Mod might be busy or waiting for game loop
                    # print(f"Mod warning: {data['error']}")
                    return None

                # Inject index into monsters if missing
                if "monsters" in data:
                    for idx, m in enumerate(data["monsters"]):
                        m["index"] = idx

                if "potions" in data:
                    for idx, p in enumerate(data["potions"]):
                        p["index"] = idx
                
                return GameState(**data)
            else:
                print(f"Error fetching state: {response.status_code} - {response.text}")
                return None
        except requests.exceptions.ConnectionError:
            # print("Could not connect to the game. Is the mod running?")
            return None
        except requests.exceptions.Timeout:
            print("Request timed out.")
            return None
        except Exception as e:
            print(f"Error parsing state: {e}")
            return None

    def submit_action(self, action: GameAction) -> Tuple[bool, Optional[dict], str]:
        try:
            payload = action.to_api_payload()
            response = requests.post(self.action_url, json=payload, timeout=2.0)
            if response.status_code == 200:
                try:
                    data = response.json()
                except json.JSONDecodeError:
                    data = None
                return True, data, "submitted"
            else:
                return False, None, f"http_{response.status_code}: {response.text}"
        except Exception as e:
            return False, None, f"exception: {e}"

    def execute_action(self, action: GameAction) -> bool:
        """Backward-compatible wrapper: True means request accepted by HTTP server."""
        ok, _, _ = self.submit_action(action)
        return ok

    def get_card_info(self, card_id: str) -> Optional[dict]:
        try:
            url = f"{self.base_url}/card_info"
            # Use POST with JSON body for robustness
            response = requests.post(url, json={"id": card_id}, timeout=2.0)
            
            if response.status_code == 200:
                return response.json()
            else:
                # print(f"Error fetching card info for {card_id}: {response.status_code}")
                return None
        except Exception as e:
            # print(f"Error fetching card info: {e}")
            return None
