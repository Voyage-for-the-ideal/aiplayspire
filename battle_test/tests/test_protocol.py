import socket
import unittest

from battle_test.protocol import ProtocolError, receive_json, send_json


class ProtocolTest(unittest.TestCase):
    def test_round_trip_uses_java_length_prefix(self):
        sender, receiver = socket.socketpair()
        try:
            send_json(sender, {"type": "PING", "n": 3})
            self.assertEqual(receive_json(receiver), {"type": "PING", "n": 3})
        finally:
            sender.close()
            receiver.close()

    def test_escapes_non_ascii_json_to_keep_wire_ascii(self):
        sender, receiver = socket.socketpair()
        try:
            send_json(sender, {"type": "PING", "value": "\u2603"})
            self.assertEqual(receive_json(receiver), {"type": "PING", "value": "\u2603"})
        finally:
            sender.close()
            receiver.close()
