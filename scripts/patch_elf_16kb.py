import os
import struct
import sys

def patch_elf_align(so_path):
    with open(so_path, 'r+b') as f:
        header = f.read(64)
        if len(header) < 64:
            return False

        if header[:4] not in (b'\x7fELF',):
            return False

        is_64bit = header[4] == 2
        if is_64bit:
            e_phoff = struct.unpack_from('<Q', header, 32)[0]
            e_phentsize = struct.unpack_from('<H', header, 54)[0]
            e_phnum = struct.unpack_from('<H', header, 56)[0]
            ph_size = 56
        else:
            e_phoff = struct.unpack_from('<I', header, 28)[0]
            e_phentsize = struct.unpack_from('<H', header, 42)[0]
            e_phnum = struct.unpack_from('<H', header, 44)[0]
            ph_size = 32

        if e_phentsize != ph_size:
            return False

        patched = False
        for i in range(e_phnum):
            offset = e_phoff + i * ph_size
            f.seek(offset)
            ph = f.read(ph_size)
            if len(ph) < ph_size:
                break

            if is_64bit:
                p_type = struct.unpack_from('<I', ph, 0)[0]
                p_align = struct.unpack_from('<Q', ph, 48)[0]
            else:
                p_type = struct.unpack_from('<I', ph, 0)[0]
                p_align = struct.unpack_from('<I', ph, 28)[0]

            if p_type == 1 and p_align == 0x1000:
                align_size = 8 if is_64bit else 4
                align_offset = 48 if is_64bit else 28
                f.seek(offset + align_offset)
                if is_64bit:
                    f.write(struct.pack('<Q', 0x4000))
                else:
                    f.write(struct.pack('<I', 0x4000))
                patched = True
                print(f"  patched PT_LOAD p_align: 0x1000 -> 0x4000")
                break

        return patched

def main():
    if len(sys.argv) < 2:
        print("Usage: python patch_elf_16kb.py <directory>")
        sys.exit(1)

    root = sys.argv[1]
    if not os.path.isdir(root):
        print(f"Directory not found: {root}")
        sys.exit(1)

    patched_count = 0
    for dirpath, _, filenames in os.walk(root):
        for fn in filenames:
            if fn.endswith('.so'):
                path = os.path.join(dirpath, fn)
                try:
                    if patch_elf_align(path):
                        patched_count += 1
                        print(f"  {path}")
                except Exception as e:
                    print(f"  ERROR {path}: {e}")

    print(f"\nPatched {patched_count} libraries")
    return 0

if __name__ == '__main__':
    sys.exit(main())
