from dataclasses import dataclass


@dataclass(frozen=True)
class ActionReceipt:
    source_state_token: str
    sequence: int
    request_id: int
    method: str
    args: tuple

    @classmethod
    def parse(cls, wire: dict) -> "ActionReceipt":
        token, sequence, request_id, method, args = (
            wire.get(k) for k in ("source_state_token", "sequence", "request_id", "method", "args")
        )
        if (not isinstance(token, str) or not isinstance(method, str) or type(sequence) is not int
                or type(request_id) is not int or not isinstance(args, list)):
            raise ValueError("Invalid driver receipt")
        return cls(token, sequence, request_id, method, tuple(args))


def verify_driver(before: dict, after: dict, method: str, args) -> None:
    expected = ActionReceipt(before["state_token"], before["sequence"] + 1, after["request_id"], method, tuple(args))
    wire = after["state"].get("driver_receipt")
    if not isinstance(wire, dict) or ActionReceipt.parse(wire) != expected:
        raise ValueError("Driver receipt does not bind the single requested action")
