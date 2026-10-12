#!/usr/bin/env python3
"""在 OnePlus .cfa 中定位体脂秤连接并 dump 其原始 L2CAP/ATT 流量。
HCI 事件布局: [0x04][evt_code][param_len][params...]; LE Meta 子事件 = params[0]=pkt[3]
"""
import struct, sys

ADDR_LE = bytes([0x80,0xea,0xca,0x00,0x70,0xdf])[::-1]

def records(path):
    d = open(path,'rb').read(); off = 16
    while off + 24 <= len(d):
        ol, il, fl, dr = struct.unpack_from(">IIII", d, off)
        pkt = d[off+24:off+24+il]; off += 24 + il
        if not pkt: break
        yield (fl & 1, pkt)

ATT = {0x01:"ATT_ERROR",0x02:"MTU_REQ",0x03:"MTU_RSP",0x04:"FIND_INFO_REQ",0x05:"FIND_INFO_RSP",
       0x06:"FIND_BY_TYPE_REQ",0x07:"FIND_BY_TYPE_RSP",0x08:"READ_BY_TYPE_REQ",0x09:"READ_BY_TYPE_RSP",
       0x0a:"READ_REQ",0x0b:"READ_RSP",0x10:"READ_BY_GROUP_REQ",0x11:"READ_BY_GROUP_RSP",
       0x12:"WRITE_REQ",0x13:"WRITE_RSP",0x16:"PREP_WRITE_REQ",0x17:"PREP_WRITE_RSP",
       0x18:"EXEC_WRITE_REQ",0x19:"EXEC_WRITE_RSP",0x1b:"NOTIFY",0x1d:"INDICATE",0x1e:"CONFIRM",0x52:"WRITE_CMD"}

def main(path, dump=True):
    conns = []   # (handle, addr, sub)
    for direction, pkt in records(path):
        if pkt[0] == 0x04 and len(pkt) >= 4 and pkt[1] == 0x3E:
            sub = pkt[3]
            if sub in (0x01, 0x0A) and len(pkt) >= 15:
                st = pkt[4]; h = struct.unpack_from("<H", pkt, 5)[0]; addr = pkt[9:15]
                conns.append((h, addr.hex(), sub))
    print("connection-complete events:", [(hex(h), a, hex(s)) for h,a,s in conns])
    sh = {h for h,a,s in conns if a == ADDR_LE.hex()}
    if not sh:
        print("SCALE not connected in", path); return
    print("scale handle(s):", [hex(x) for x in sh])
    if not dump: return
    buf = {}
    for direction, pkt in records(path):
        if pkt[0] != 0x02: continue
        b = pkt[1:]
        if len(b) < 4: continue
        hdr = struct.unpack_from("<H", b, 0)[0]
        h = hdr & 0xFFF; pb = (hdr >> 12) & 3
        if h not in sh: continue
        key = (h, direction)
        pdu = b[4:]
        if pb == 1:
            if key in buf: buf[key][0] += pdu
            else: continue
        else:
            buf[key] = [bytearray(pdu)]
        data = buf[key][0]
        if len(data) < 4: continue
        l2len = struct.unpack_from("<H", data, 0)[0]
        cid = struct.unpack_from("<H", data, 2)[0]
        if len(data) < 2 + l2len: continue
        pdu = bytes(data[2:2+l2len])
        del buf[key]
        arrow = "TX->" if direction == 0 else "<-RX"
        if cid == 0x0004 and pdu:
            op = pdu[0]
            print(f"{arrow} ATT {ATT.get(op, hex(op)):16s} {pdu.hex()}")
        else:
            print(f"{arrow} cid={cid:#06x} {pdu.hex()}")

if __name__ == "__main__":
    main(sys.argv[1], dump=(len(sys.argv) < 3))
