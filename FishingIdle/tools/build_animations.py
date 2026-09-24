#!/usr/bin/env python3
"""把 AI 生成的精灵图集统一处理成游戏可用的动画图集。

流程：探测网格 → 切帧 → 裁内容包围盒 → alpha 预乘缩放 → 重排标准网格。
输出到 assets/art/ 并生成 anim.json 描述文件。

用法: python3 tools/build_animations.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from split_sheet import detect_grid, extract_frames, normalize, pack  # noqa: E402
from PIL import Image  # noqa: E402
import numpy as np  # noqa: E402

ART = "app/src/main/assets/art"

# (源文件, 输出名, 输出每行帧数)
JOBS = [
    ("seaweed_sheet.png", "seaweed", 4),
    ("boat_sheet.png", "boat", 4),
    # 常见
    ("fish_common_sheet.png", "fish_common", 4),
    ("fw_jiyu_sheet.png", "fw_jiyu", 4),
    ("fw_niqiu_sheet.png", "fw_niqiu", 4),
    # 稀有
    ("fish_rare_sheet.png", "fish_rare", 4),
    ("fw_liyu_sheet.png", "fw_liyu", 4),
    ("fw_huangsang_sheet.png", "fw_huangsang", 4),
    # 史诗
    ("fish_epic_sheet.png", "fish_epic", 4),
    ("fw_qingyu_sheet.png", "fw_qingyu", 4),
    ("fw_heiyu_sheet.png", "fw_heiyu", 4),
        # 传说
    ("fish_p8.png", "fw_daliyu", 4, (2, 4)),
    ("fw_xunyu_sheet.png", "fw_xunyu", 4),
        # 秘境
                        ("fw_eel_sheet.png", "fw_eel", 4),
        ("fw_arowana_sheet.png", "fw_arowana", 4),
        # 新补充的独立鱼种
    ("fy_yongyu_sheet.png", "fy_yongyu", 4),
    ("fy_bianyu_sheet.png", "fy_bianyu", 4),
    ("fy_guiyu_sheet.png", "fy_guiyu", 4),
    ("fy_qiaozui_sheet.png", "fy_qiaozui", 4),
    ("fy_ziyu_sheet.png", "fy_ziyu", 4),
    ("fy_hongqi_sheet.png", "fy_hongqi", 4),
    ("fy_pangpi_sheet.png", "fy_pangpi", 4),
    ("fy_maisui_sheet.png", "fy_maisui", 4),
    ("fy_moroko_sheet.png", "fy_moroko", 4),
    ("fy_huzi_sheet.png", "fy_huzi", 4),
    ("fy_lingyu_sheet.png", "fy_lingyu", 4),
    ("fy_shengyu_sheet.png", "fy_shengyu", 4),
    ("fy_gouyu_sheet.png", "fy_gouyu", 4),
    ("fy_wuchang_sheet.png", "fy_wuchang", 4),
    ("fy_tongyu_sheet.png", "fy_tongyu", 4),
    ("fy_changwen_sheet.png", "fy_changwen", 4),
    ("fy_shatang_sheet.png", "fy_shatang", 4),
    ("fy_yanzhi_sheet.png", "fy_yanzhi", 4),
    ("fy_huangshan_sheet.png", "fy_huangshan", 4),
    ("fy_baixun_sheet.png", "fy_baixun", 4),
    # 凑齐 54 张独立外观
    ("fz_huanyu_sheet.png", "fz_huanyu", 4),
    ("fz_wuli_sheet.png", "fz_wuli", 4),
    ("fz_jingli_sheet.png", "fz_jingli", 4),
    ("fz_jinji_sheet.png", "fz_jinji", 4, (2, 4)),
    ("fz_bailian_sheet.png", "fz_bailian", 4),
    ("fz_hualu_sheet.png", "fz_hualu", 4),
    ("fz_hailu_sheet.png", "fz_hailu", 4),
    ("fz_huaqiu_sheet.png", "fz_huaqiu", 4),
    ("fz_dalinqiu_sheet.png", "fz_dalinqiu", 4),
    ("fz_huangwei_sheet.png", "fz_huangwei", 4),
    ("fz_hongli_sheet.png", "fz_hongli", 4),
    ("fz_jinli_sheet.png", "fz_jinli", 4),
    ("fz_hudie_sheet.png", "fz_hudie", 4),
    ("fz_baishan_sheet.png", "fz_baishan", 4),
    ("fz_daheiyu_sheet.png", "fz_daheiyu", 4),
    ("fz_junian_sheet.png", "fz_junian", 4),
    ("fz_juli_sheet.png", "fz_juli", 4),
    ("fz_xunwang_sheet.png", "fz_xunwang", 4),
    ("fz_jinlong_sheet.png", "fz_jinlong", 4),
]


def count_interior_holes(path):
    """统计每帧内部还剩多少透明洞（外部背景不算）。

    这是判断"精灵被镂空"的可靠指标：从四边泛洪标出外部，
    剩下的透明像素就是被内容包住的洞。
    """
    a = np.array(Image.open(path).convert("RGBA"))
    alpha = a[:, :, 3]
    h, w = alpha.shape
    content = alpha > 10

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

    holes = int(((~content) & (~exterior)).sum())
    total = int(content.sum()) or 1
    return holes, holes / total


def main():
    ok, fail = 0, 0
    for job in JOBS:
        src_name, out_name, cols = job[0], job[1], job[2]
        # 第 4 个元素可强制指定网格 (rows, cols)，用于自动探测判错的素材
        forced = job[3] if len(job) > 3 else None
        src = os.path.join(ART, src_name)
        if not os.path.exists(src):
            print(f"  !! 缺少源文件 {src_name}")
            fail += 1
            continue

        if forced is not None:
            im = Image.open(src).convert("RGBA")
            rows, grid_cols, ratio = forced[0], forced[1], 0.0
        else:
            im, (rows, grid_cols), ratio = detect_grid(src)
        frames = extract_frames(im, rows, grid_cols)
        norm = normalize(frames)
        if not norm:
            print(f"  !! {src_name}: 无有效帧")
            fail += 1
            continue

        sheet, c, r, fw, fh = pack(norm, cols)
        dst = os.path.join(ART, f"{out_name}_anim.png")
        sheet.save(dst)

        cfg = {
            "image": f"{out_name}_anim.png",
            "frameWidth": fw, "frameHeight": fh,
            "columns": c, "rows": r, "frameCount": len(norm),
        }
        with open(os.path.join(ART, f"{out_name}_anim.json"), "w") as f:
            json.dump(cfg, f, indent=2)

        holes, ratio = count_interior_holes(dst)
        warn = "  << 仍有镂空，需重做素材" if ratio > 0.02 else ""
        print(f"  {out_name:14s} 网格{rows}x{grid_cols} → {len(norm):2d}帧  "
              f"单帧{fw}x{fh}  内部镂空={holes}px({ratio:.2%}){warn}")
        if ratio > 0.02:
            fail += 1
            continue
        ok += 1

    print(f"\n完成 {ok} 个，失败 {fail} 个")


if __name__ == "__main__":
    main()
