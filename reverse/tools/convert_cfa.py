#!/usr/bin/env python3
"""把 OnePlus 的 .cfa (btsnoop + 每记录前 8 字节时间戳) 转换为标准 btsnoop，
以便 tshark 直接解码。"""
import struct, sys

def convert(src, dst):
    d = open(src, "rb").read()
    assert d[:8] == b"btsnoop\x00", "not btsnoop"
    out = bytearray(d[:16])  # magic + version + datalink
    off = 16
    n = 0
    while off + 24 <= len(d):
        ol, il, fl, dr = struct.unpack_from(">IIII", d, off)
        off += 16
        off += 8  # skip the inserted timestamp
        pkt = d[off:off + il]
        off += il
        if len(pkt) < 1:
            break
        out += struct.pack(">IIII", ol, il, fl, dr)
        out += pkt
        n += 1
    open(dst, "wb").write(out)
    print(f"{src} -> {dst}  records={n}")

if __name__ == "__main__":
    convert(sys.argv[1], sys.argv[2])
