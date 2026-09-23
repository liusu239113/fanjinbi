#!/usr/bin/env python3
"""检查动画图集的帧质量，找出坏帧。

判据：
- 空帧/内容过少
- 面积与同图其他帧差异过大（可能是糊帧、缺帧、姿态崩坏）
- 宽高比异常（姿态翻转、拉伸）
- 内部镂空比例过高（被抠穿）

用法: python3 tools/check_quality.py [art目录]
"""
import os
import sys
from collections import deque

import numpy as np
from PIL import Image

ART = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/art"


def frame_stats(cell):
    """返回 (内容像素数, 宽高比, 镂空比例)。"""
    al = cell[:, :, 3] > 12
    n = int(al.sum())
    if n == 0:
        return 0, 0.0, 0.0
    ys, xs = np.where(al)
    h = ys.max() - ys.min() + 1
    w = xs.max() - xs.min() + 1
    ratio = w / max(h, 1)

    # 内部镂空：从四边泛洪标出外部，剩下的是被包住的洞
    H, W = al.shape
    exterior = np.zeros((H, W), dtype=bool)
    q = deque()
    for x in range(W):
        for y in (0, H - 1):
            if not al[y, x] and not exterior[y, x]:
                exterior[y, x] = True
                q.append((y, x))
    for y in range(H):
        for x in (0, W - 1):
            if not al[y, x] and not exterior[y, x]:
                exterior[y, x] = True
                q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < H and 0 <= nx < W and not al[ny, nx] and not exterior[ny, nx]:
                exterior[ny, nx] = True
                q.append((ny, nx))
    holes = int(((~al) & (~exterior)).sum())
    return n, ratio, holes / max(n, 1)


def check(path):
    cfg_path = path.replace("_anim.png", "_anim.json")
    cols, rows = 4, 2
    if os.path.exists(cfg_path):
        import json
        with open(cfg_path) as f:
            c = json.load(f)
        cols, rows = c.get("columns", 4), c.get("rows", 2)

    im = Image.open(path).convert("RGBA")
    fw, fh = im.width // cols, im.height // rows
    stats = []
    for r in range(rows):
        for c in range(cols):
            cell = np.array(im.crop((c * fw, r * fh, (c + 1) * fw, (r + 1) * fh)))
            n, ratio, hole = frame_stats(cell)
            if n < 60:
                continue
            stats.append((r * cols + c, n, ratio, hole))

    if not stats:
        return None

    areas = np.array([s[1] for s in stats], dtype=float)
    ratios = np.array([s[2] for s in stats], dtype=float)
    med_area = np.median(areas)
    med_ratio = np.median(ratios)

    issues = []
    for idx, n, ratio, hole in stats:
        if n < med_area * 0.35:
            issues.append(f"帧{idx}内容过少({n:.0f} vs 中位{med_area:.0f})")
        if med_ratio > 0 and (ratio > med_ratio * 2.2 or ratio < med_ratio / 2.2):
            issues.append(f"帧{idx}宽高比异常({ratio:.2f} vs 中位{med_ratio:.2f})")
        if hole > 0.06:
            issues.append(f"帧{idx}镂空{hole:.0%}")
    return len(stats), issues


def main():
    files = sorted(f for f in os.listdir(ART) if f.endswith("_anim.png"))
    bad = 0
    print(f"{'资源':16s} {'帧数':>4s}  问题")
    print("-" * 70)
    for f in files:
        res = check(os.path.join(ART, f))
        if res is None:
            print(f"{f:16s}    0  全空")
            bad += 1
            continue
        n, issues = res
        if issues:
            bad += 1
            print(f"{f:16s} {n:4d}  " + "; ".join(issues[:4]))
        else:
            print(f"{f:16s} {n:4d}  ok")
    print(f"\n共 {len(files)} 个资源，{bad} 个有问题")


if __name__ == "__main__":
    main()
