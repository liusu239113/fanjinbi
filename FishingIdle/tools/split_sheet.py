#!/usr/bin/env python3
"""把 AI 生成的精灵图集切成独立帧，并重新打包成规整网格。

AI 出图时行列数不固定（要 4x2 可能给 3x3），这里不假设布局：
穷举候选网格，选「每格都有内容、且内容不触碰格子边界」的那个 —— 内容触边
说明这一刀切进了帧里，网格选错了。

用法:
  python3 split_sheet.py <输入图集> <输出png> [输出json]
"""
import json
import os
import sys

import numpy as np
from PIL import Image

# 候选 (行, 列)，按"越规整越优先"排序
CANDIDATES = [
    (2, 4), (4, 2), (3, 3), (2, 2), (1, 4), (4, 1), (2, 3), (3, 2),
    (2, 6), (6, 2), (1, 8), (8, 1), (2, 8), (8, 2), (3, 4), (4, 3),
    (1, 2), (2, 1), (1, 3), (3, 1), (1, 6), (6, 1), (1, 1),
]


def separation_ratio(alpha, rows, cols, edge_margin=2, min_pixels=60):
    """衡量这个网格切得干不干净。

    返回 (所有格子都有内容?, 边界接触像素占比)。
    占比接近 0 = 每一刀都落在帧之间的空白处，网格正确。
    占比大 = 有刀切进了帧内部。
    """
    h, w = alpha.shape
    ch, cw = h // rows, w // cols
    if ch < 10 or cw < 10:
        return False, 1.0

    total = 0
    edge = 0
    for r in range(rows):
        for c in range(cols):
            cell = alpha[r * ch:(r + 1) * ch, c * cw:(c + 1) * cw]
            n = int(cell.sum())
            if n < min_pixels:
                return False, 1.0        # 空格子 → 网格切错了
            total += n
            edge += int(
                cell[:edge_margin, :].sum() + cell[-edge_margin:, :].sum()
                + cell[:, :edge_margin].sum() + cell[:, -edge_margin:].sum()
            )
    if total == 0:
        return False, 1.0
    return True, edge / total


def detect_grid(path, max_edge_ratio=0.010):
    """穷举候选网格，返回切得最细且干净的那个。

    先筛掉「有空格子」和「内容触边」的候选，再从中取帧数最多的 ——
    否则 1x1 这种平凡解会因为天然不触边而被选中。
    """
    im = Image.open(path).convert("RGBA")
    alpha = np.array(im)[:, :, 3] > 12

    valid = []
    for (r, c) in CANDIDATES:
        ok, ratio = separation_ratio(alpha, r, c)
        if ok and ratio <= max_edge_ratio:
            valid.append((r * c, ratio, r, c))

    if not valid:
        # 放宽阈值再试一次（AI 出图可能有轻微粘连）
        for (r, c) in CANDIDATES:
            ok, ratio = separation_ratio(alpha, r, c)
            if ok and ratio <= 0.05:
                valid.append((r * c, ratio, r, c))

    if not valid:
        return im, (2, 4), 1.0

    # 帧数最多优先；同帧数取更干净的
    valid.sort(key=lambda t: (-t[0], t[1]))
    _, ratio, r, c = valid[0]
    return im, (r, c), ratio


def extract_frames(im, rows, cols):
    a = np.array(im)
    h, w = a.shape[:2]
    ch, cw = h // rows, w // cols
    frames = []
    for r in range(rows):
        for c in range(cols):
            cell = a[r * ch:(r + 1) * ch, c * cw:(c + 1) * cw]
            if (cell[:, :, 3] > 12).sum() < 60:
                continue
            frames.append(cell)
    return frames


def resize_premultiplied(img, nw, nh):
    """带 alpha 预乘的缩放。

    两个关键点：
    1. 先预乘 alpha 再缩放，插值只在"已着色"像素之间发生，
       否则透明区的残留颜色会渗进边缘。
    2. 用 BILINEAR 而不是 LANCZOS —— LANCZOS 的负瓣会在
       黑色描边与透明背景这种高对比边缘产生振铃，出来一圈暗色光晕。
    """
    if img.width == nw and img.height == nh:
        return img

    a = np.array(img).astype(np.float32)
    alpha = a[:, :, 3:4] / 255.0
    pm = np.concatenate([a[:, :, :3] * alpha, a[:, :, 3:4]], axis=2)
    pm_img = Image.fromarray(np.clip(pm, 0, 255).astype(np.uint8), "RGBA")
    pm_resized = np.array(pm_img.resize((nw, nh), Image.BILINEAR)).astype(np.float32)

    out_a = pm_resized[:, :, 3:4]
    safe = np.maximum(out_a / 255.0, 1e-4)
    rgb = np.clip(pm_resized[:, :, :3] / safe, 0, 255)
    result = np.concatenate([rgb, out_a], axis=2)
    return Image.fromarray(result.astype(np.uint8), "RGBA")


def clean_fringe(img, threshold=8):
    """把几乎全透明的像素彻底清空，避免残留颜色在缩放时渗出。"""
    a = np.array(img).copy()
    a[a[:, :, 3] < threshold] = 0
    return Image.fromarray(a, "RGBA")


def _dilate(mask, iterations=1):
    """4 邻域膨胀，用来判断某个像素离主体有多远。"""
    m = mask.copy()
    for _ in range(iterations):
        out = m.copy()
        out[1:, :] |= m[:-1, :]
        out[:-1, :] |= m[1:, :]
        out[:, 1:] |= m[:, :-1]
        out[:, :-1] |= m[:, 1:]
        m = out
    return m


def prune_stray(img, alpha_threshold=12, max_stray_distance=2):
    """清除离主体较远的孤立噪点，**保留主体的抗锯齿边缘**。

    AI 生成的透明图集，主体外围常散落一些零星的半透明/彩色小点，
    放大后会变成一圈脏点。这里的做法不是按 alpha 阈值硬切（那会把
    柔和的抗锯齿边缘也切碎，形成虚线破边），而是：
    先取出实心主体，向四周膨胀几像素得到一个"允许区域"，
    只丢弃落在这个区域之外的像素。主体自身的边缘完全不动。
    """
    from collections import deque

    a = np.array(img).astype(np.uint8)
    solid = a[:, :, 3] > 160          # 实心部分，抗锯齿边缘不算
    if not solid.any():
        return img

    h, w = solid.shape
    labels = np.zeros((h, w), dtype=np.int32)
    best_label, best_size = 0, 0
    cur = 0
    for y0 in range(h):
        for x0 in range(w):
            if solid[y0, x0] and labels[y0, x0] == 0:
                cur += 1
                q = deque([(y0, x0)])
                labels[y0, x0] = cur
                n = 0
                while q:
                    y, x = q.popleft()
                    n += 1
                    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        ny, nx = y + dy, x + dx
                        if 0 <= ny < h and 0 <= nx < w and solid[ny, nx] and labels[ny, nx] == 0:
                            labels[ny, nx] = cur
                            q.append((ny, nx))
                if n > best_size:
                    best_size, best_label = n, cur

    if best_label == 0:
        return img

    main = labels == best_label
    allowed = _dilate(main, max_stray_distance)

    any_content = a[:, :, 3] > alpha_threshold
    stray = any_content & ~allowed
    if not stray.any():
        return img

    out = a.copy()
    out[stray] = 0
    return Image.fromarray(out, "RGBA")


def repair_interior(img, alpha_threshold=10):
    """修补精灵内部的透明/半透明空洞。

    透明背景生图的常见缺陷：浅色区域（例如鲨鱼的白肚皮）会被模型当成背景
    一并抠掉，或者只给一个很低的 alpha，结果精灵身上出现镂空。
    这里先区分"外部背景"和"被内容包住的内部"，再把内部空洞用邻近像素的颜色补实。

    做法：从四条边泛洪标记外部 → 剩下的非内容像素就是内部洞 →
    反复把已填充像素的颜色向外扩散一层，直到填满。
    """
    a = np.array(img).astype(np.float32)
    h, w = a.shape[:2]
    alpha = a[:, :, 3]
    content = alpha > alpha_threshold

    if not content.any():
        return img

    # 从四边泛洪，标记外部背景
    exterior = np.zeros((h, w), dtype=bool)
    stack = []
    for x in range(w):
        for y in (0, h - 1):
            if not content[y, x] and not exterior[y, x]:
                exterior[y, x] = True
                stack.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if not content[y, x] and not exterior[y, x]:
                exterior[y, x] = True
                stack.append((y, x))
    while stack:
        y, x = stack.pop()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not content[ny, nx] and not exterior[ny, nx]:
                exterior[ny, nx] = True
                stack.append((ny, nx))

    # 只修补「完全透明」的内部洞。
    # 千万不要把抗锯齿的半透明边缘也一起填实 —— 那会把干净的柔边
    # 换成邻近色，产生一圈杂色锯齿（这是之前踩过的坑）。
    holes = (~content) & (~exterior)
    if not holes.any():
        return img

    rgb = a[:, :, :3].copy()
    out_alpha = a[:, :, 3].copy()
    filled = content.copy()

    for _ in range(600):
        todo = holes & ~filled
        if not todo.any():
            break

        cnt = np.zeros((h, w), dtype=np.float32)
        acc = np.zeros((h, w, 3), dtype=np.float32)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            shifted_filled = np.roll(np.roll(filled, dy, axis=0), dx, axis=1)
            shifted_rgb = np.roll(np.roll(rgb, dy, axis=0), dx, axis=1)
            # np.roll 会环绕，把绕回来的那一行/列排除掉
            if dy == 1:
                shifted_filled[0, :] = False
            elif dy == -1:
                shifted_filled[-1, :] = False
            if dx == 1:
                shifted_filled[:, 0] = False
            elif dx == -1:
                shifted_filled[:, -1] = False

            m = shifted_filled & todo
            cnt += m
            acc += shifted_rgb * m[:, :, None]

        upd = cnt > 0
        if not upd.any():
            break
        rgb[upd] = acc[upd] / cnt[upd][:, None]
        out_alpha[upd] = 255
        filled |= upd

    a[:, :, :3] = rgb
    a[:, :, 3] = out_alpha
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def normalize(frames, pad_ratio=0.08):
    """裁到内容包围盒 → 补内部洞 → 居中放到统一画布。

    **不做重采样。** 这里的缩放是"二次缩放"（源图已经是一次生成的结果），
    插值会在黑色描边与透明背景的高对比边缘产生脏点，放大后尤其明显。
    保持源分辨率，缩放交给渲染层按屏幕密度一次完成，边缘最干净。
    """
    cropped = []
    for f in frames:
        al = f[:, :, 3] > 12
        ys, xs = np.where(al)
        if len(xs) == 0:
            continue
        cropped.append(f[ys.min():ys.max() + 1, xs.min():xs.max() + 1])
    if not cropped:
        return None

    max_h = max(c.shape[0] for c in cropped)
    max_w = max(c.shape[1] for c in cropped)
    canvas_h = int(max_h * (1 + pad_ratio * 2))
    canvas_w = int(max_w * (1 + pad_ratio * 2))

    out = []
    for c in cropped:
        img = Image.fromarray(c, "RGBA")
        # 只清掉离主体较远的孤立噪点，主体自身的抗锯齿边缘保持原样。
        # 不要再做 repair_interior —— AI 已经把浅色区域正确填充了，
        # 强行补洞只会破坏干净的黑描边。
        img = prune_stray(img)

        canvas = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
        canvas.paste(img, ((canvas_w - img.width) // 2, (canvas_h - img.height) // 2), img)
        out.append(canvas)
    return out


def pack(frames, cols):
    fw, fh = frames[0].size
    rows = (len(frames) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * fw, rows * fh), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.paste(f, ((i % cols) * fw, (i // cols) * fh))
    return sheet, cols, rows, fw, fh


def main():
    src = sys.argv[1]
    dst = sys.argv[2]
    out_json = sys.argv[3] if len(sys.argv) > 3 else None
    out_cols = int(sys.argv[4]) if len(sys.argv) > 4 else 4

    im, (rows, cols), score = detect_grid(src)
    frames = extract_frames(im, rows, cols)
    norm = normalize(frames)
    if not norm:
        print(f"  !! {os.path.basename(src)}: 无有效帧")
        return 1

    sheet, c, r, fw, fh = pack(norm, out_cols)
    os.makedirs(os.path.dirname(dst) or ".", exist_ok=True)
    sheet.save(dst)
    flag = "  << 可能切错" if score > 300 else ""
    print(f"  {os.path.basename(src)}: 网格 {rows}x{cols} (penalty={score:.0f}) → {len(norm)} 帧 → {c}x{r} 单帧 {fw}x{fh}{flag}")

    if out_json:
        with open(out_json, "w") as f:
            json.dump({
                "image": os.path.basename(dst),
                "frameWidth": fw, "frameHeight": fh,
                "columns": c, "rows": r, "frameCount": len(norm),
            }, f, indent=2)
    return 0


if __name__ == "__main__":
    sys.exit(main())
