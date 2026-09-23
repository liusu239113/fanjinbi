#!/usr/bin/env python3
"""资源后处理：精灵抠边裁切 / UI 九宫格切片 / 应用图标多密度导出。

用法: python3 tools/process_art.py <assets/art 目录>
"""
import os
import sys
import numpy as np
from PIL import Image

PAD = 2  # 裁切后保留的透明内边距，防止边缘被采样削掉


def trim_sprite(path):
    """把带 alpha 的精灵裁到内容包围盒 + 统一内边距。"""
    im = Image.open(path).convert("RGBA")
    arr = np.array(im)
    alpha = arr[:, :, 3]
    ys, xs = np.where(alpha > 8)
    if len(xs) == 0:
        return False
    x0, x1 = xs.min(), xs.max() + 1
    y0, y1 = ys.min(), ys.max() + 1
    crop = im.crop((x0, y0, x1, y1))

    # 补回统一内边距
    w, h = crop.size
    canvas = Image.new("RGBA", (w + PAD * 2, h + PAD * 2), (0, 0, 0, 0))
    canvas.paste(crop, (PAD, PAD))
    canvas.save(path)
    return True


def find_corner_inset(path):
    """探测木质面板四角护角的像素范围，作为九宫格的安全切片位置。

    取四个角落象限内「金色护角」像素包围盒的最大外延，再留一点余量。
    """
    im = Image.open(path).convert("RGB")
    arr = np.array(im).astype(int)
    h, w, _ = arr.shape
    r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]
    # 金色护角：暖黄、高红绿、低蓝
    gold = (r > 140) & (g > 100) & (b < 140) & (r - b > 45)

    qw, qh = w // 4, h // 4
    quadrants = [
        gold[:qh, :qw],            # 左上
        gold[:qh, w - qw:],        # 右上
        gold[h - qh:, :qw],        # 左下
        gold[h - qh:, w - qw:],    # 右下
    ]
    max_x, max_y = 0, 0
    for q in quadrants:
        ys, xs = np.where(q)
        if len(xs) == 0:
            continue
        max_x = max(max_x, xs.max() + 1, q.shape[1] - xs.min())
        max_y = max(max_y, ys.max() + 1, q.shape[0] - ys.min())

    if max_x == 0 or max_y == 0:          # 没探测到护角，退回保守值
        max_x, max_y = w // 6, h // 6

    # 留 6px 余量，并保证中间至少留出 1/3 的可拉伸区
    inset_x = min(max_x + 6, w // 3)
    inset_y = min(max_y + 6, h // 3)
    return inset_x, inset_y


def make_nine_patch(src, dst, inset_x, inset_y):
    """生成 Android .9.png：外侧 1px 透明边框用黑线标记拉伸区与内容区。"""
    im = Image.open(src).convert("RGBA")
    w, h = im.size
    out = Image.new("RGBA", (w + 2, h + 2), (0, 0, 0, 0))
    out.paste(im, (1, 1))
    px = out.load()

    # 上/左边缘黑线 = 可拉伸区域（四角与四条边之间）
    for x in range(1 + inset_x, 1 + w - inset_x):
        px[x, 0] = (0, 0, 0, 255)
    for y in range(1 + inset_y, 1 + h - inset_y):
        px[0, y] = (0, 0, 0, 255)
    # 下/右边缘黑线 = 内容填充区域
    for x in range(1 + inset_x, 1 + w - inset_x):
        px[x, h + 1] = (0, 0, 0, 255)
    for y in range(1 + inset_y, 1 + h - inset_y):
        px[w + 1, y] = (0, 0, 0, 255)

    out.save(dst)
    return out.size


def export_icons(src, res_dir):
    """导出应用图标的多密度 mipmap + 自适应图标前景。"""
    im = Image.open(src).convert("RGBA")
    densities = {
        "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192,
    }
    for name, size in densities.items():
        d = os.path.join(res_dir, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        im.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))
        im.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher_round.png"))

    # 自适应图标前景：内容缩到 66% 安全区居中
    fg_dir = os.path.join(res_dir, "mipmap-xxxhdpi")
    os.makedirs(fg_dir, exist_ok=True)
    for name, size in densities.items():
        d = os.path.join(res_dir, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        inner = int(size * 0.66)
        fg = im.resize((inner, inner), Image.LANCZOS)
        off = (size - inner) // 2
        canvas.paste(fg, (off, off), fg)
        canvas.save(os.path.join(d, "ic_launcher_foreground.png"))


def main():
    art = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/art"
    res = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/res"

    # 1. 精灵裁边
    sprites = [
        "boat", "bobber", "fish_common", "fish_rare", "fish_epic",
        "fish_legend", "gold_coin", "icon_helper", "icon_hover",
        "icon_multiplier", "icon_reflip", "icon_speed", "icon_value", "rod",
    ]
    for name in sprites:
        p = os.path.join(art, name + ".png")
        if os.path.exists(p):
            before = Image.open(p).size
            trim_sprite(p)
            print(f"  trim {name:18s} {before} -> {Image.open(p).size}")

    # 2. 木质面板九宫格
    panel = os.path.join(art, "wood_panel.png")
    if os.path.exists(panel):
        ix, iy = find_corner_inset(panel)
        dst_dir = os.path.join(res, "drawable-nodpi")
        os.makedirs(dst_dir, exist_ok=True)
        dst = os.path.join(dst_dir, "panel_wood.9.png")
        size = make_nine_patch(panel, dst, ix, iy)
        print(f"  nine-patch panel_wood.9.png size={size} inset=({ix},{iy})")

    # 3. 应用图标
    icon = os.path.join(art, "app_icon.png")
    if os.path.exists(icon):
        export_icons(icon, res)
        print("  icons exported to mipmap-*")

    print("资源后处理完成")


if __name__ == "__main__":
    main()
