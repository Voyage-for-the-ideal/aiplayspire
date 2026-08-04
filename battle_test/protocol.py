"""The DataInputStream/DataOutputStream UTF framing used by AiServer."""

from __future__ import annotations

import json
import socket
import struct
from typing import Any


class ProtocolError(RuntimeError):
    pass


def _modified_utf8(value: str) -> bytes:
    # JSON requests are ASCII by contract. Java's modified UTF-8 is therefore
    # byte-for-byte UTF-8 here, while retaining the 65,535 byte frame limit.
    try:
        return value.encode("ascii")
    except UnicodeEncodeError as exc:
        raise ProtocolError("wire JSON must be ASCII") from exc


def send_json(sock: socket.socket, message: dict[str, Any]) -> None:
    encoded = json.dumps(message, ensure_ascii=True, separators=(",", ":"))
    payload = _modified_utf8(encoded)
    if len(payload) > 0xFFFF:
        raise ProtocolError("Java writeUTF frame exceeds 65,535 bytes")
    sock.sendall(struct.pack(">H", len(payload)) + payload)


def receive_text(sock: socket.socket) -> str:
    header = _receive_exact(sock, 2)
    length = struct.unpack(">H", header)[0]
    # AiServer responses are ASCII JSON or the ASCII DONE marker.
    return _receive_exact(sock, length).decode("utf-8")


def receive_json(sock: socket.socket) -> dict[str, Any]:
    text = receive_text(sock)
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError as exc:
        raise ProtocolError("expected JSON response, got %r" % text) from exc
    if not isinstance(parsed, dict):
        raise ProtocolError("response JSON must be an object")
    return parsed


def _receive_exact(sock: socket.socket, size: int) -> bytes:
    chunks: list[bytes] = []
    remaining = size
    while remaining:
        chunk = sock.recv(remaining)
        if not chunk:
            raise ProtocolError("socket closed mid-frame")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)
