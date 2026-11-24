"""Read-only readiness probes. Never submit an action or launch a process."""
import argparse
import json
import os
import socket
import time
import urllib.request

from battle_engine import protocol


def probe(kind):
    if kind == "gateway":
        with socket.create_connection(("127.0.0.1", int(os.environ.get("AIP_PORT", 5125))), 2) as connection:
            connection.settimeout(2)
            protocol.send(connection, json.dumps({"type": "PING"}))
            reply = json.loads(protocol.receive(connection))
            if reply.get("type") != "PONG" or not reply.get("ready") or reply.get("mode") != "cpp-only":
                raise ValueError("unexpected gateway ping response")
    else:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        port = int(os.environ.get("AIP_API_PORT", 5000))
        with opener.open(f"http://127.0.0.1:{port}/state", timeout=3) as response:
            state = json.load(response)
        if not isinstance(state, dict) or not state or "error" in state:
            raise ValueError("empty game state")
        return state


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("game", "gateway"))
    parser.add_argument("--timeout", type=float, default=90)
    args = parser.parse_args()
    deadline = time.monotonic() + args.timeout
    while True:
        try:
            probe(args.kind)
            print(args.kind + " ready")
            return
        except (OSError, ValueError) as error:
            if time.monotonic() >= deadline:
                raise SystemExit(f"{args.kind} readiness deadline: {type(error).__name__}")
            time.sleep(1)


if __name__ == "__main__":
    main()
