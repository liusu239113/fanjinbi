#!/usr/bin/env python3
"""标准精灵图处理管线：AI 生图 → 游戏可用的动画图集。

**所有序列帧素材都必须走这个脚本**，不允许各写各的临时脚本 ——
之前无人机就是这么丢掉白色机身的：临时脚本绕过了 normalize()，
里面"补内部空洞"那一步没跑，白机身被抠出来的洞就留在了成品里。

流程（与 build_animations.py 完全一致的那套）：
    切帧 → normalize（裁内容包围盒 + 补内部空洞 + 居中到统一画布）
         → 重排成标准网格 → 缩放 → 写 <名字>_anim.png + <名字>_anim.json

用法:
    python3 tools/build_sprite.py <源图> <输出名> --rows 2 --cols 4 --frame-w 220
    python3 tools/build_sprite.py <源图> <输出名> --rows 2 --cols 4 --frame-w 220 --out-cols 4

说明:
    --rows/--cols  源图的网格（几行几列），不确定就先用 split_sheet.detect_grid 探
    --frame-w      成品单帧宽度（世界单位换算用：运行时按这个宽度缩放到目标尺寸）
    --out-cols     成品每行放几帧，默认 4（和现有素材一致）
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from split_sheet import detect_grid, extract_frames, normalize, pack, resize_premultiplied  # noqa: E402

from PIL import Image  # noqa: E402
import numpy as np  # noqa: E402

ART = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "art")


def interior_hole_ratio(img):
    """内部空洞占比：从四边泛洪标出外部，剩下的透明像素就是洞。"""
    a = np.array(img.convert("RGBA"))[:, :, 3]
    content = a > 10
    if not content.any():
        return 1.0
    h, w = content.shape
    ext = np.zeros((h, w), bool)
    stack = []
    for x in range(w):
        for y in (0, h - 1):
            if not content[y, x] and not ext[y, x]:
                ext[y, x] = True
                stack.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if not content[y, x] and not ext[y, x]:
                ext[y, x] = True
                stack.append((y, x))
    while stack:
        y, x = stack.pop()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not content[ny, nx] and not ext[ny, nx]:
                ext[ny, nx] = True
                stack.append((ny, nx))
    holes = (~content) & (~ext)
    return float(holes.sum()) / float(content.sum())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("name", help="输出名（生成 <name>_anim.png / <name>_anim.json）")
    ap.add_argument("--rows", type=int, default=0)
    ap.add_argument("--cols", type=int, default=0)
    ap.add_argument("--frame-w", type=int, default=148)
    ap.add_argument("--out-cols", type=int, default=4)
    args = ap.parse_args()

    if args.rows and args.cols:
        im = Image.open(args.src).convert("RGBA")
        rows, cols = args.rows, args.cols
    else:
        im, (rows, cols), ratio = detect_grid(args.src)
        print(f"自动探测网格 {rows}x{cols}（分离度 {ratio:.3f}）")

    # extract_frames / normalize 走的是 numpy 数组，别在中途换成 PIL Image
    frames = extract_frames(im, rows, cols)
    norm = normalize(frames)          # ← 补内部空洞就在这里，千万别绕过
    if not norm:
        print("!! 没有有效帧")
        return 1

    sheet, c, r, fw, fh = pack(norm, args.out_cols)
    if args.frame_w and fw != args.frame_w:
        scale = args.frame_w / fw
        sheet = resize_premultiplied(sheet, int(round(sheet.width * scale)), int(round(sheet.height * scale)))
        fw = int(round(fw * scale))
        fh = int(round(fh * scale))

    os.makedirs(ART, exist_ok=True)
    png = os.path.join(ART, f"{args.name}_anim.png")
    sheet.save(png)
    with open(os.path.join(ART, f"{args.name}_anim.json"), "w") as f:
        json.dump({
            "image": f"{args.name}_anim.png",
            "frameWidth": fw, "frameHeight": fh,
            "columns": c, "rows": r, "frameCount": len(norm),
        }, f, indent=2)

    worst = max(interior_hole_ratio(t) for t in norm)
    print(f"{args.name}: {len(norm)} 帧 单帧 {fw}x{fh} → {png}")
    print(f"  内部空洞最差 {worst * 100:.2f}%" + ("  <<< 需要检查（白色部件可能被抠掉）" if worst > 0.05 else "  ok"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
