import os
import struct
import sys
import zipfile

def check_elf_align(so_path):
    with open(so_path, 'rb') as f:
        header = f.read(64)
        if len(header) < 4 or header[:4] != b'\x7fELF':
            return None
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
            return None
        for i in range(e_phnum):
            f.seek(e_phoff + i * ph_size)
            ph = f.read(ph_size)
            if is_64bit:
                p_type = struct.unpack_from('<I', ph, 0)[0]
                p_align = struct.unpack_from('<Q', ph, 48)[0]
            else:
                p_type = struct.unpack_from('<I', ph, 0)[0]
                p_align = struct.unpack_from('<I', ph, 28)[0]
            if p_type == 1:
                return p_align
    return None

def main():
    if len(sys.argv) < 2:
        print("Usage: python verify_elf_16kb.py <apk_path>")
        sys.exit(1)

    apk_path = sys.argv[1]
    if not os.path.isfile(apk_path):
        print(f"File not found: {apk_path}")
        sys.exit(1)

    ok = True
    with zipfile.ZipFile(apk_path, 'r') as zf:
        for entry in zf.namelist():
            if entry.startswith('lib/') and entry.endswith('.so'):
                data = zf.read(entry)
                if len(data) < 4 or data[:4] != b'\x7fELF':
                    continue
                is_64 = data[4] == 2
                if is_64:
                    phoff = struct.unpack_from('<Q', data, 32)[0]
                    phent = struct.unpack_from('<H', data, 54)[0]
                    phnum = struct.unpack_from('<H', data, 56)[0]
                    phs = 56
                else:
                    phoff = struct.unpack_from('<I', data, 28)[0]
                    phent = struct.unpack_from('<H', data, 42)[0]
                    phnum = struct.unpack_from('<H', data, 44)[0]
                    phs = 32
                for i in range(phnum):
                    off = phoff + i * phs
                    ph = data[off:off + phs]
                    if is_64:
                        pt = struct.unpack_from('<I', ph, 0)[0]
                        pa = struct.unpack_from('<Q', ph, 48)[0]
                    else:
                        pt = struct.unpack_from('<I', ph, 0)[0]
                        pa = struct.unpack_from('<I', ph, 28)[0]
                    if pt == 1:
                        status = "OK" if pa >= 0x4000 else "FAIL"
                        if status == "FAIL":
                            ok = False
                        print(f"  {status}: {entry} -> p_align=0x{pa:x}")
                        break

    if ok:
        print("\nAll libraries have 16 KB+ alignment")
    else:
        print("\nSome libraries still have <16 KB alignment!")
        sys.exit(1)

if __name__ == '__main__':
    main()
