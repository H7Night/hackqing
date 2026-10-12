#!/usr/bin/env python3
"""把 libconfig.so 内嵌的期望签名 SHA1 替换为本 App 的签名 SHA1，
使其 ClientConfigJNI.init() 的签名校验通过（否则会 System.exit）。

用法: patch_libconfig.py <libconfig.so> <本App签名SHA1(40位大写hex)>
"""
import sys

ORIG = b"7ECF252315A51D96885F7F618D62937002DF6F87"

def patch(path, new_sha1):
    d = bytearray(open(path, "rb").read())
    new = new_sha1.strip().upper().encode()
    assert len(new) == 40, "SHA1 必须是 40 位十六进制"
    n = d.count(ORIG)
    if n == 0:
        # 已打过补丁？
        print(f"{path}: 未找到原始签名串（可能已打过补丁）")
        return
    d = d.replace(ORIG, new)
    open(path, "wb").write(d)
    print(f"{path}: 替换 {n} 处 -> {new.decode()}")

if __name__ == "__main__":
    patch(sys.argv[1], sys.argv[2])
