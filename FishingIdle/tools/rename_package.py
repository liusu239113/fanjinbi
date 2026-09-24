#!/usr/bin/env python3
"""把工程整体换包名 + 换游戏名（一次性脚本，改完可留档）。

包名 com.taptap.fishingidle → com.dshx.game.SU
游戏名 钓鱼大师 → 钓鱼人生:放置大师模拟

做三件事：搬源码目录（包路径必须和目录一致）、改所有 package/import、
改配置里的 namespace / applicationId / 应用名 / 主题名 / 工程名。
"""
import os
import re
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OLD_PKG = "com.taptap.fishingidle"
NEW_PKG = "com.dshx.game.SU"
OLD_NAME = "钓鱼大师"
NEW_NAME = "钓鱼人生:放置大师模拟"

OLD_DIR = os.path.join(ROOT, "app/src/main/java/com/taptap/fishingidle")
NEW_DIR = os.path.join(ROOT, "app/src/main/java/com/dshx/game/SU")
OLD_TEST_DIR = os.path.join(ROOT, "app/src/test/java/com/taptap/fishingidle")
NEW_TEST_DIR = os.path.join(ROOT, "app/src/test/java/com/dshx/game/SU")


def move_dir(old, new):
    if not os.path.isdir(old):
        print(f"  (跳过) {old} 不存在")
        return
    os.makedirs(os.path.dirname(new), exist_ok=True)
    if os.path.isdir(new):
        shutil.rmtree(new)
    shutil.move(old, new)
    # 清掉空的旧包目录
    parent = os.path.dirname(old)
    while parent.startswith(os.path.join(ROOT, "app/src")) and not os.listdir(parent):
        os.rmdir(parent)
        parent = os.path.dirname(parent)
    print(f"  目录 {os.path.relpath(old, ROOT)} → {os.path.relpath(new, ROOT)}")


def rewrite(path):
    with open(path, encoding="utf-8") as f:
        src = f.read()
    out = src.replace(OLD_PKG, NEW_PKG).replace(OLD_NAME, NEW_NAME)
    if out != src:
        with open(path, "w", encoding="utf-8") as f:
            f.write(out)
        return True
    return False


def main():
    print("1) 搬源码目录")
    move_dir(OLD_DIR, NEW_DIR)
    move_dir(OLD_TEST_DIR, NEW_TEST_DIR)

    print("2) 改包名 / 应用名")
    changed = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in (".git", "build", ".gradle", ".kotlin", "__pycache__")]
        for name in filenames:
            if not name.endswith((".kt", ".kts", ".xml", ".pro", ".md", ".yml", ".properties")):
                continue
            path = os.path.join(dirpath, name)
            if rewrite(path):
                changed.append(os.path.relpath(path, ROOT))
    for c in sorted(changed):
        print("  改了", c)
    print(f"共 {len(changed)} 个文件")


if __name__ == "__main__":
    main()
