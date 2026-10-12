#!/usr/bin/env python3
"""Parse a btsnoop (HCI H4, datalink 1002) capture and dump ATT/GATT traffic.

Usage: python3 btsnoop_att.py <file.cfa> [mac_filter]
"""
import struct
import sys

ATT_NAMES = {
    0x01: "ERROR", 0x02: "MTU_REQ", 0x03: "MTU_RSP",
    0x04: "FIND_INFO_REQ", 0x05: "FIND_INFO_RSP",
    0x06: "FIND_BY_TYPE_REQ", 0x07: "FIND_BY_TYPE_RSP",
    0x08: "READ_BY_TYPE_REQ", 0x09: "READ_BY_TYPE_RSP",
    0x0A: "READ_REQ", 0x0B: "READ_RSP",
    0x0C: "READ_BLOB_REQ", 0x0D: "READ_BLOB_RSP",
    0x10: "READ_BY_GROUP_REQ", 0x11: "READ_BY_GROUP_RSP",
    0x12: "WRITE_REQ", 0x13: "WRITE_RSP",
    0x1B: "WRITE_CMD", 0x1D: "NOTIFY", 0x1E: "INDICATE", 0x1F: "CONFIRM",
}


def u16(b, o):
    return struct.unpack_from("<H", b, o)[0]


def parse(path, mac_filter=None):
    data = open(path, "rb").read()
    assert data[:8] == b"btsnoop\x00", "not a btsnoop file"
    datalink = struct.unpack_from(">I", data, 12)[0]
    print(f"# file={path} datalink={datalink} size={len(data)}")
    off = 16
    # reassembly buffers keyed by acl handle
    buf = {}
    idx = 0
    while off + 24 <= len(data):
        orig_len, inc_len, flags, drops = struct.unpack_from(">IIII", data, off)
        off += 16
        off += 8  # OnePlus .cfa inserts an 8-byte big-endian microsecond timestamp
        pkt = data[off:off + inc_len]
        off += inc_len
        if len(pkt) < 1:
            continue
        direction = flags & 1  # 0 = host->controller (TX), 1 = controller->host (RX)
        typ = pkt[0]
        if typ != 0x02:  # only ACL
            continue
        body = pkt[1:]
        if len(body) < 4:
            continue
        hdr = u16(body, 0)
        handle = hdr & 0x0FFF
        pb = (hdr >> 12) & 0x3
        l2len = u16(body, 2)
        payload = body[4:]
        if pb == 1:  # continuation
            if handle in buf:
                buf[handle][0] += payload
                buf[handle][1] = l2len
            else:
                continue
        else:
            buf[handle] = [bytearray(payload), l2len]
        b, need = buf[handle]
        if len(b) < need:
            continue
        frame = bytes(b[:need])
        del buf[handle]
        if len(frame) < 4:
            continue
        cid = u16(frame, 0)
        if cid != 0x0004:
            continue
        att = frame[2:]
        if not att:
            continue
        op = att[0]
        name = ATT_NAMES.get(op, f"0x{op:02X}")
        arrow = "TX ->" if direction == 0 else "RX <-"
        extra = ""
        if op in (0x1D, 0x1E):  # notify/indicate
            h = u16(att, 1)
            val = att[3:]
            extra = f"handle=0x{h:04X} len={len(val)} val={val.hex()}"
        elif op in (0x1B, 0x52, 0x12, 0x13):
            h = u16(att, 1)
            val = att[3:]
            extra = f"handle=0x{h:04X} len={len(val)} val={val.hex()}"
        else:
            extra = f"raw={att[:24].hex()}"
        print(f"[{idx:05d}] {arrow} {name:18s} {extra}")
        idx += 1


if __name__ == "__main__":
    parse(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
