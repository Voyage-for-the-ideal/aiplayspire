import requests
import json
from typing import Optional, Tuple
from .models import GameState, GameAction

# 状态获取错误分类: OK / MOD_BUSY(Mod 返回 error) / HTTP_ERROR(非 200)
# / TIMEOUT / CONNECTION_ERROR(断线) / PARSE_ERROR(JSON 解析失败) / UNKNOWN
ERR_OK = "OK"
ERR_MOD_BUSY = "MOD_BUSY"
ERR_HTTP = "HTTP_ERROR"
ERR_TIMEOUT = "TIMEOUT"
ERR_CONNECTION = "CONNECTION_ERROR"
ERR_PARSE = "PARSE_ERROR"
ERR_UNKNOWN = "UNKNOWN"


class GameClient:
    def __init__(self, base_url: str = "http://localhost:5000"):
        self.base_url = base_url
        self.state_url = f"{base_url}/state"
        self.action_url = f"{base_url}/action"

    def get_state(self) -> Optional[GameState]:
        """兼容接口: 失败统一返回 None (错误详情见 get_state_detailed)。"""
        state, _, _ = self.get_state_detailed()
        return state

    def get_state_detailed(self) -> Tuple[Optional[GameState], str, str]:
        """获取状态并分类失败原因。

        返回 (state, err_kind, err_msg):
        - 成功时 err_kind=ERR_OK, err_msg="";
        - 失败时 state=None, err_kind 区分 Mod 忙/HTTP 错误/超时/断线/解析失败。
        """
        try:
            response = requests.get(self.state_url, timeout=2.0)
            if response.status_code == 200:
                try:
                    data = response.json()
                except json.JSONDecodeError:
                    return None, ERR_PARSE, "Invalid JSON response"
                except Exception as e:
                    return None, ERR_PARSE, f"JSON parse error: {e}"

                # Mod might be busy or waiting for game loop
                if "error" in data:
                    return None, ERR_MOD_BUSY, str(data["error"])

                # Inject index into monsters if missing
                if "monsters" in data:
                    for idx, m in enumerate(data["monsters"]):
                        m["index"] = idx

                if "potions" in data:
                    for idx, p in enumerate(data["potions"]):
                        p["index"] = idx

                try:
                    return GameState(**data), ERR_OK, ""
                except Exception as e:
                    return None, ERR_PARSE, f"state parse error: {e}"
            else:
                return None, ERR_HTTP, f"http_{response.status_code}: {response.text[:200]}"
        except requests.exceptions.ConnectionError:
            return None, ERR_CONNECTION, "connection error"
        except requests.exceptions.Timeout:
            return None, ERR_TIMEOUT, "request timed out"
        except Exception as e:
            return None, ERR_UNKNOWN, str(e)

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
