#!/usr/bin/env python3
"""清掉源码里被 shell 转义坏掉的 `\\!` / `\\=`。

用 bash heredoc 批量改 Kotlin 时，`!` 有时会被转义成 `\\!`，
于是 `!=` 变成 `\\!=`，编译就报一堆莫名其妙的语法错误。
这个脚本扫全工程把它还原回去，改完打印每个文件修了几处。
"""
import os
import sys

ROOTS = ["app/src/main/java", "app/src/test/java"]
BAD = {"\\!": "!", "\\=": "="}


def main():
    total = 0
    for root in ROOTS:
        for dirpath, _, files in os.walk(root):
            for name in files:
                if not name.endswith(".kt"):
                    continue
                path = os.path.join(dirpath, name)
                with open(path, encoding="utf-8") as f:
                    src = f.read()
                fixed = src
                count = 0
                for bad, good in BAD.items():
                    count += fixed.count(bad)
                    fixed = fixed.replace(bad, good)
                if count:
                    with open(path, "w", encoding="utf-8") as f:
                        f.write(fixed)
                    print(f"{path}: 修了 {count} 处")
                    total += count
    print(f"合计 {total} 处" if total else "干净，无需修复")
    return 0


if __name__ == "__main__":
    sys.exit(main())
