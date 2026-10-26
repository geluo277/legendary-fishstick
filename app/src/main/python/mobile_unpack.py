# Android adaptation for texture-only extraction from Eggy Party PC NXPK files.
import os, gzip, shutil, zipfile
from pathlib import Path
import zstandard
from PIL import Image
import nxpk_mode3 as N
import image_convert

IMAGE_EXTS = {".kta", ".dds", ".ktx", ".png", ".jpg", ".jpeg", ".tga", ".bmp", ".webp", ".gif"}

def _safe_rel(path, signature, package_name):
    if not path:
        return f"_unresolved/{package_name}/{signature:08x}"
    path = path.replace("\\", "/").lstrip("/")
    parts = [p for p in path.split("/") if p not in ("", ".", "..")]
    if not parts:
        return f"_unresolved/{package_name}/{signature:08x}"
    return "/".join(parts)

def extract_images(npk_path, output_dir, pathdict_path, jpg=False):
    """Extract only texture-like payloads. Returns a JSON-serializable summary."""
    npk_path = Path(npk_path)
    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)
    resolver = N.PathResolver.from_file(pathdict_path)
    # Fail early when the header/index does not match the supported NXPK mode-3 format.
    # Without this validation, an incompatible package can be silently skipped entry-by-entry,
    # producing a misleading 22-byte empty ZIP.
    header, entries = N.read_index(npk_path, validate=True, resolver=resolver)
    count_ok = count_fail = count_skip = 0
    dctx = zstandard.ZstdDecompressor()
    with open(npk_path, "rb") as src:
        for i, entry in enumerate(entries):
            try:
                if entry.packed_size == 0 and entry.unpacked_size == 0:
                    count_skip += 1
                    continue
                src.seek(entry.offset)
                packed = src.read(entry.packed_size)
                if len(packed) != entry.packed_size:
                    raise ValueError("NPK 数据不完整")
                if entry.compression == 3:
                    payload = dctx.decompress(packed, max_output_size=max(1, entry.unpacked_size))
                elif entry.compression == 0:
                    payload = packed
                else:
                    count_skip += 1
                    continue
                if len(payload) != entry.unpacked_size:
                    raise ValueError("解压后大小不匹配")
                real_path, _ = resolver.resolve(entry.signature)
                suffix = Path(real_path or "").suffix.lower()
                kind = image_convert.classify(payload, suffix)
                if kind not in ("ktx1", "ktx2", "dds", "png", "jpeg", "tga", "bmp", "webp", "gif"):
                    count_skip += 1
                    continue
                if kind == "ktx2":
                    # Keep unsupported KTX2 intact rather than silently corrupting it.
                    rel = _safe_rel(real_path, entry.signature, npk_path.name)
                    target = out / (rel + ".ktx2")
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(payload)
                    count_skip += 1
                    continue
                rel = _safe_rel(real_path, entry.signature, npk_path.name)
                target_ext = ".jpg" if jpg else ".png"
                target = out / str(Path(rel).with_suffix(target_ext))
                target.parent.mkdir(parents=True, exist_ok=True)
                tmp = target.with_name(target.stem + ".tmp.png")
                try:
                    image_convert.convert_image(payload, suffix, tmp)
                    with Image.open(tmp) as im:
                        if jpg:
                            if im.mode not in ("RGB", "L"):
                                bg = Image.new("RGB", im.size, (255,255,255))
                                if "A" in im.getbands():
                                    bg.paste(im, mask=im.getchannel("A"))
                                else:
                                    bg.paste(im.convert("RGB"))
                                bg.save(target, "JPEG", quality=95, optimize=True)
                            else:
                                im.convert("RGB").save(target, "JPEG", quality=95, optimize=True)
                        else:
                            im.save(target, "PNG")
                    tmp.unlink(missing_ok=True)
                    count_ok += 1
                except Exception:
                    tmp.unlink(missing_ok=True)
                    # Preserve original bytes for unsupported texture formats.
                    fallback = out / str(Path(rel).with_suffix(suffix or ".bin"))
                    fallback.parent.mkdir(parents=True, exist_ok=True)
                    fallback.write_bytes(payload)
                    count_fail += 1
            except Exception:
                count_fail += 1
    # Do not report success if nothing was written. This commonly indicates an unsupported
    # package format/version, a wrong index key, or a path/texture recognition mismatch.
    written_files = [p for p in out.rglob("*") if p.is_file()]
    if not written_files:
        raise RuntimeError(
            "没有提取出任何文件。该 NPK 可能不兼容当前内置的 NXPK Mode 3 索引格式/密钥，"
            "也可能没有可识别的贴图。请使用支持的 PC 版 NXPK 包测试，并检查游戏版本。"
        )
    return {"total": len(entries), "exported": count_ok, "fallback_or_failed": count_fail,
            "skipped": count_skip, "written_files": len(written_files), "output": str(out)}
