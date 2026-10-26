"""Offline batch conversion of ASTC/DXT KTX textures inside ZIP archives."""
from __future__ import annotations
import os
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path, PurePosixPath

KTX1_ID = b"\xABKTX 11\xBB\r\n\x1A\n"
ASTC_INTERNALS = set(range(0x93B0, 0x93C0)) | set(range(0x93D0, 0x93E0))
MAX_ENTRY_SIZE = 512 * 1024 * 1024


def _safe_rel(name: str) -> Path | None:
    # ZIP names always use slash separators. Reject absolute paths and traversal.
    p = PurePosixPath(name.replace('\\', '/'))
    if p.is_absolute() or any(part in ('..', '') for part in p.parts):
        return None
    if not p.parts or p.parts[0].endswith(':'):
        return None
    return Path(*p.parts)


def _is_ktx(blob: bytes) -> bool:
    return blob.startswith(KTX1_ID) or blob.startswith(b"\xABKTX 20\xBB\r\n\x1A\n")


def _ktx1_internal_format(blob: bytes) -> int | None:
    if len(blob) < 36 or not blob.startswith(KTX1_ID):
        return None
    # KTX1 internalFormat is the fifth uint32 after the identifier.
    import struct
    endian = struct.unpack_from('<I', blob, 12)[0]
    if endian != 0x04030201:
        return None
    return struct.unpack_from('<I', blob, 28)[0]


def _run_astcenc(exe: str, src: Path, dst: Path) -> None:
    errors = []
    for mode in ('-dl', '-ds', '-dh'):
        try:
            r = subprocess.run([exe, mode, str(src), str(dst)], stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, text=True, timeout=180)
        except subprocess.TimeoutExpired:
            errors.append(f'{mode}: timeout')
            continue
        if r.returncode == 0 and dst.exists() and dst.stat().st_size > 8:
            return
        errors.append(f'{mode}: {(r.stderr or r.stdout or str(r.returncode))[-500:]}')
        try:
            dst.unlink()
        except FileNotFoundError:
            pass
    raise ValueError('ASTC 解码失败；' + ' | '.join(errors))


def convert_zip(input_zip: str, output_zip: str, astcenc_exe: str) -> str:
    src_zip, out_zip = Path(input_zip), Path(output_zip)
    if not zipfile.is_zipfile(src_zip):
        raise ValueError('所选文件不是有效 ZIP 压缩包。')
    exe = Path(astcenc_exe)
    if not exe.exists() or not os.access(exe, os.X_OK):
        raise ValueError('APK 内未找到可运行的 ASTC 解码器。请重新构建并安装完整 APK。')

    converted = 0
    preserved = 0
    failed = 0
    skipped = 0
    errors = []
    out_zip.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix='notx_ktx_') as td:
        work = Path(td)
        with zipfile.ZipFile(src_zip, 'r') as zin, zipfile.ZipFile(out_zip, 'w', zipfile.ZIP_DEFLATED, compresslevel=3) as zout:
            for idx, info in enumerate(zin.infolist()):
                if info.is_dir():
                    continue
                rel = _safe_rel(info.filename)
                if rel is None:
                    skipped += 1
                    errors.append(f'跳过不安全路径：{info.filename}')
                    continue
                if info.file_size > MAX_ENTRY_SIZE:
                    skipped += 1
                    errors.append(f'跳过超大文件（超过 512 MiB）：{info.filename}')
                    continue
                try:
                    payload = zin.read(info)
                except Exception as e:
                    failed += 1
                    errors.append(f'读取失败：{info.filename} ({e})')
                    continue

                # Preserve non-KTX files byte-for-byte in the output archive.
                if not _is_ktx(payload):
                    zout.writestr(str(rel).replace(os.sep, '/'), payload)
                    preserved += 1
                    continue

                internal = _ktx1_internal_format(payload)
                in_path = work / f'input_{idx}.ktx'
                out_path = work / f'output_{idx}.png'
                in_path.write_bytes(payload)
                try:
                    if internal in ASTC_INTERNALS:
                        _run_astcenc(str(exe), in_path, out_path)
                    else:
                        # The Python converter handles KTX1 DXT1/DXT5. ASTC goes through astcenc.
                        from ktx_to_png import convert_bytes
                        convert_bytes(payload, out_path, info.filename)
                    out_rel = rel.with_suffix('.png')
                    zout.write(out_path, str(out_rel).replace(os.sep, '/'))
                    converted += 1
                except Exception as e:
                    # Keep unconverted source data so a failed conversion never destroys it.
                    zout.writestr(str(rel).replace(os.sep, '/'), payload)
                    failed += 1
                    errors.append(f'转换失败：{info.filename} ({e})')
                finally:
                    try: in_path.unlink()
                    except FileNotFoundError: pass
                    try: out_path.unlink()
                    except FileNotFoundError: pass

    summary = [f'批量转换完成：成功转换 {converted} 个 KTX 纹理；保留其他文件 {preserved} 个；转换失败 {failed} 个；跳过 {skipped} 个。',
               '成功转换的纹理已改为 PNG；失败的纹理会保留原文件，避免丢失数据。']
    if errors:
        summary.append('失败/跳过详情（最多 12 条）：\n' + '\n'.join(errors[:12]))
    return '\n'.join(summary)
