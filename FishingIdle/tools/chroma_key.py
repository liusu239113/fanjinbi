#!/usr/bin/env python3
"""把「纯色键控底」的素材抠成透明 PNG，并处理干净边缘。

**为什么要有这个工具**：直接在透明底上生图时，白色/浅色部件经常被模型
当成背景一起抠掉（白机身、白肚皮、白网），事后几乎补不回来 ——
因为那些"洞"往往和外部背景连通，判断不出该不该补。

所以带白色部件的素材一律：**先用纯洋红底生图**（`transparent=false`），
再用这个工具键控。流程：

    键控去底 → 腐蚀 1px 去掉混色边 → 把内部颜色向外扩散补回这一圈 → 羽化 alpha

这样边缘不会残留洋红，也不会出现被吃掉的白色区域。

用法: python3 tools/chroma_key.py <输入图> <输出图> [--key FF00FF] [--erode 1] [--feather 1]
"""
import argparse
import sys

import numpy as np
from PIL import Image


def _shift_min(mask, dy, dx):
    """把布尔掩码沿 (dy,dx) 平移，越界部分按 False 处理。"""
    out = np.zeros_like(mask)
    h, w = mask.shape
    ys = slice(max(0, dy), h + min(0, dy))
    xs = slice(max(0, dx), w + min(0, dx))
    ys2 = slice(max(0, -dy), h + min(0, -dy))
    xs2 = slice(max(0, -dx), w + min(0, -dx))
    out[ys2, xs2] = mask[ys, xs]
    return out


def erode(mask, iterations=1):
    """4 邻域腐蚀，用来削掉与背景混色的那一圈像素。"""
    m = mask.copy()
    for _ in range(iterations):
        cur = m.copy()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            cur &= _shift_min(m, dy, dx)
        m = cur
    return m


def box_blur(a, radius=1):
    """对 float 数组做一次 3x3（或 5x5）均值模糊，用来羽化 alpha。"""
    out = a.copy()
    for _ in range(radius):
        acc = np.zeros_like(out)
        cnt = np.zeros_like(out)
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                sh = np.roll(np.roll(out, dy, axis=0), dx, axis=1)
                if dy == 1:
                    sh[0, :] = 0
                elif dy == -1:
                    sh[-1, :] = 0
                if dx == 1:
                    sh[:, 0] = 0
                elif dx == -1:
                    sh[:, -1] = 0
                acc += sh
                cnt += 1
        out = acc / cnt
    return out


def spread_colors(rgb, seed, iterations):
    """把 [seed] 区域的颜色逐圈向外扩散 [iterations] 圈，覆盖外侧的像素颜色。

    两个用途：补回被腐蚀掉的边缘一圈；把半透明边上的键色残留换成最近的画面颜色
    （否则浅色部件边缘会挂一圈洋红）。
    """
    rgb = rgb.copy()
    filled = seed.copy()
    todo = ~seed
    for _ in range(iterations):
        acc = np.zeros_like(rgb)
        cnt = np.zeros(seed.shape, dtype=np.float32)
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                if dy == 0 and dx == 0:
                    continue
                sh_f = _shift_min(filled, dy, dx)
                sh_rgb = np.roll(np.roll(rgb, dy, axis=0), dx, axis=1)
                if dy == 1:
                    sh_rgb = sh_rgb.copy(); sh_rgb[0, :] = 0
                elif dy == -1:
                    sh_rgb = sh_rgb.copy(); sh_rgb[-1, :] = 0
                if dx == 1:
                    sh_rgb = sh_rgb.copy(); sh_rgb[:, 0] = 0
                elif dx == -1:
                    sh_rgb = sh_rgb.copy(); sh_rgb[:, -1] = 0
                m = sh_f & todo
                acc += sh_rgb * m[:, :, None]
                cnt += m
        upd = cnt > 0
        if not upd.any():
            break
        rgb[upd] = acc[upd] / cnt[upd][:, None]
        filled |= upd
    return rgb


def chroma_key(img, key=(255, 0, 255), hard=0.30, soft=0.62, erode_px=1, feather_px=1):
    """返回抠好的 RGBA 图。

    [hard]/[soft] 是"键色程度"的双阈值：小于 hard 判为内容，
    大于 soft 判为背景，之间线性过渡。
    """
    a = np.array(img.convert("RGB")).astype(np.float32)
    r, g, b = a[:, :, 0], a[:, :, 1], a[:, :, 2]

    if key == (255, 0, 255) or key == (0, 255, 0) or key == (0, 0, 255):
        # 洋红 / 绿 / 蓝：用"键色通道高于第三通道"的程度衡量
        if key == (255, 0, 255):
            keyness = (np.minimum(r, b) - g) / 255.0
        elif key == (0, 255, 0):
            keyness = (g - np.maximum(r, b)) / 255.0
        else:
            keyness = (b - np.maximum(r, g)) / 255.0
    else:
        kr, kg, kb = key
        dist = np.sqrt((r - kr) ** 2 + (g - kg) ** 2 + (b - kb) ** 2)
        keyness = 1.0 - dist / 441.7

    # 内容掩码：键色程度低的地方是内容
    content = keyness < hard
    if erode_px > 0:
        content = erode(content, erode_px)

    # 把内容的颜色向外扩散若干圈：既补回被腐蚀掉的那一圈，
    # 也把半透明边缘上残留的键色换成最近的画面颜色
    rgb = spread_colors(a, content, erode_px + 4)

    alpha = content.astype(np.float32)
    if feather_px > 0:
        alpha = box_blur(alpha, feather_px)
    alpha = np.clip(alpha * 255.0, 0, 255)

    out = np.dstack([rgb, alpha]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("dst")
    ap.add_argument("--key", default="FF00FF")
    ap.add_argument("--erode", type=int, default=1)
    ap.add_argument("--feather", type=int, default=1)
    args = ap.parse_args()

    key = tuple(int(args.key[i:i + 2], 16) for i in (0, 2, 4))
    img = Image.open(args.src)
    out = chroma_key(img, key=key, erode_px=args.erode, feather_px=args.feather)
    out.save(args.dst)
    arr = np.array(out)[:, :, 3]
    print(f"{args.src} → {args.dst}  透明占比 {float((arr < 16).mean()):.3f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
