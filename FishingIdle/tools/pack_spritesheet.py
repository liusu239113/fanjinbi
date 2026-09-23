#!/usr/bin/env python3
"""把视频抽出的帧序列打包成游戏用精灵表。

背景是纯色（浅蓝水色），用从四角泛洪填充的方式抠掉 —— 只移除与边缘连通的背景，
水草内部相近颜色的像素不会被误伤。

用法:
  python3 pack_spritesheet.py <帧目录> <输出png> [列数] [输出json]
"""
import json
import os
import sys
from collections import deque

import numpy as np
from PIL import Image


def key_out_background(img, tolerance=60):
    """从四角泛洪填充，把连通的背景变透明，并做边缘羽化。"""
    rgb = np.array(img.convert("RGB")).astype(int)
    h, w, _ = rgb.shape

    # 取四角颜色的均值作为背景色
    corners = np.array([rgb[0, 0], rgb[0, w - 1], rgb[h - 1, 0], rgb[h - 1, w - 1]])
    bg = corners.mean(axis=0)

    # 与背景色的距离
    dist = np.sqrt(((rgb - bg) ** 2).sum(axis=2))
    is_bg_like = dist < tolerance

    # 泛洪：只清除与边缘连通的背景区域
    visited = np.zeros((h, w), dtype=bool)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if is_bg_like[y, x] and not visited[y, x]:
                visited[y, x] = True
                q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if is_bg_like[y, x] and not visited[y, x]:
                visited[y, x] = True
                q.append((y, x))

    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not visited[ny, nx] and is_bg_like[ny, nx]:
                visited[ny, nx] = True
                q.append((ny, nx))

    alpha = np.where(visited, 0, 255).astype(np.uint8)

    # 羽化：对 alpha 做一次 3x3 均值，软化锯齿边
    a = alpha.astype(float) / 255.0
    padded = np.pad(a, 1, mode="edge")
    blurred = (
        padded[:-2, :-2] + padded[:-2, 1:-1] + padded[:-2, 2:]
        + padded[1:-1, :-2] + padded[1:-1, 1:-1] + padded[1:-1, 2:]
        + padded[2:, :-2] + padded[2:, 1:-1] + padded[2:, 2:]
    ) / 9.0
    # 保留主体不透明，只柔化边缘
    soft = np.where(a > 0.85, 1.0, blurred)
    alpha = (soft * 255).astype(np.uint8)

    out = np.dstack([np.array(img.convert("RGB")), alpha])
    return Image.fromarray(out, "RGBA")


def pack(frames, cols, fw, fh):
    """把帧按网格拼成一张精灵表。"""
    rows = (len(frames) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * fw, rows * fh), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        c, r = i % cols, i // cols
        sheet.paste(f, (c * fw, r * fh))
    return sheet, rows


def main():
    src_dir = sys.argv[1]
    out_png = sys.argv[2]
    cols = int(sys.argv[3]) if len(sys.argv) > 3 else 8
    out_json = sys.argv[4] if len(sys.argv) > 4 else None

    names = sorted(f for f in os.listdir(src_dir) if f.endswith(".png"))
    if not names:
        print("没有找到帧文件")
        return 1

    frames = []
    for n in names:
        im = Image.open(os.path.join(src_dir, n))
        frames.append(key_out_background(im))

    fw, fh = frames[0].size
    sheet, rows = pack(frames, cols, fw, fh)
    os.makedirs(os.path.dirname(out_png) or ".", exist_ok=True)
    sheet.save(out_png)

    print(f"精灵表: {out_png}  尺寸={sheet.size}  帧数={len(frames)}  网格={cols}x{rows}  单帧={fw}x{fh}")

    if out_json:
        cfg = {
            "image": os.path.basename(out_png),
            "frameWidth": fw,
            "frameHeight": fh,
            "columns": cols,
            "rows": rows,
            "frameCount": len(frames),
        }
        with open(out_json, "w") as f:
            json.dump(cfg, f, indent=2)
        print(f"配置: {out_json}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
