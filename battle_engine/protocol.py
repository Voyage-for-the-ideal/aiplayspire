"""Java DataInput/OutputStream modified UTF-8, including NUL and UTF-16 surrogates."""
import struct


def encode(text):
    units = text.encode("utf-16-be", "surrogatepass")
    result = bytearray()
    for (unit,) in struct.iter_unpack(">H", units):
        if 0 < unit < 128:
            result.append(unit)
        elif unit < 2048:
            result.extend((0xC0 | unit >> 6, 0x80 | unit & 63))
        else:
            result.extend((0xE0 | unit >> 12, 0x80 | unit >> 6 & 63, 0x80 | unit & 63))
    if len(result) > 65535:
        raise ValueError("writeUTF payload exceeds 65535 bytes")
    return bytes(result)


def decode(data):
    units = bytearray()
    i = 0
    while i < len(data):
        first = data[i]
        i += 1
        if 0 < first < 128:
            unit = first
        elif first & 0xE0 == 0xC0:
            if i >= len(data) or data[i] & 0xC0 != 0x80:
                raise ValueError("invalid modified UTF-8 continuation")
            unit = (first & 31) << 6 | data[i] & 63
            i += 1
            if unit < 128 and unit != 0:
                raise ValueError("overlong modified UTF-8")
        elif first & 0xF0 == 0xE0:
            if i + 1 >= len(data) or any(x & 0xC0 != 0x80 for x in data[i:i + 2]):
                raise ValueError("invalid modified UTF-8 continuation")
            unit = (first & 15) << 12 | (data[i] & 63) << 6 | data[i + 1] & 63
            i += 2
            if unit < 2048:
                raise ValueError("overlong modified UTF-8")
        else:
            raise ValueError("invalid modified UTF-8 leading byte")
        units.extend(struct.pack(">H", unit))
    return units.decode("utf-16-be", "surrogatepass")


def exact(sock, size):
    result = bytearray()
    while len(result) < size:
        chunk = sock.recv(size - len(result))
        if not chunk:
            raise EOFError("peer disconnected inside writeUTF frame")
        result.extend(chunk)
    return bytes(result)


def receive_bytes(sock):
    size, = struct.unpack(">H", exact(sock, 2))
    return exact(sock, size)


def send_bytes(sock, payload):
    if len(payload) > 65535:
        raise ValueError("writeUTF payload exceeds 65535 bytes")
    sock.sendall(struct.pack(">H", len(payload)) + payload)


def send(sock, text):
    send_bytes(sock, encode(text))


def receive(sock):
    return decode(receive_bytes(sock))
