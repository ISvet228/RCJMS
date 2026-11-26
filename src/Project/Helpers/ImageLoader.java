package Project.Helpers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

public final class ImageLoader {
    private static final long MAX_FILE_BYTES = 512L * 1024 * 1024;
    private static final long MAX_PIXELS = 100_000_000L;
    private static final String[] OWN_EXTENSIONS = {"tga", "icb", "vda", "vst", "targa", "pcx", "ico", "cur", "pbm", "pgm", "ppm", "pnm", "pam", "qoi", "psd"};
    private static final String[] KNOWN_UNSUPPORTED = {"webp", "avif", "heic", "heif", "svg", "jxl"};

    private ImageLoader() { }

    //region Public API
    private static volatile java.util.Set<String> extensionSet;

    private static java.util.Set<String> extensionSet() {
        java.util.Set<String> cached = extensionSet;
        if (cached != null) return cached;
        synchronized (ImageLoader.class) {
            if (extensionSet == null) {
                ImageIO.scanForPlugins();
                TreeSet<String> set = new TreeSet<>();
                for (String suffix : ImageIO.getReaderFileSuffixes()) if (suffix != null && !suffix.isBlank()) set.add(suffix.toLowerCase(Locale.ROOT));
                set.addAll(Arrays.asList(OWN_EXTENSIONS));
                set.add("jpeg");
                set.add("tif");
                extensionSet = java.util.Collections.unmodifiableSet(set);
            }
            return extensionSet;
        }
    }
    public static boolean hasImageExtension(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return false;
        String extension = name.substring(dot + 1);
        return extensionSet().contains(extension);
    }
    public static String formatName(File file) {
        try {
            byte[] head = new byte[32];
            int n;
            try (var in = Files.newInputStream(file.toPath())) { n = in.readNBytes(head, 0, head.length); }
            String sniffed = sniff(head, n, file.getName());
            return sniffed == null ? "?" : sniffed;
        } catch (IOException exception) { return "?"; }
    }
    public static BufferedImage read(File file) throws IOException {
        if (file.length() > MAX_FILE_BYTES) throw new IOException("file is too large (" + file.length() / (1024 * 1024) + " MB)");
        byte[] data = Files.readAllBytes(file.toPath());
        return read(data, file.getName());
    }

    public static BufferedImage read(byte[] data, String nameHint) throws IOException {
        if (data.length < 4) throw new IOException("the file is empty or too short");
        String format = sniff(data, Math.min(data.length, 32), nameHint);
        if (format != null && isUnsupported(format))
            throw new IOException(format + " pictures need a codec this game does not have. Convert the picture to PNG first.");
        IOException first = null;
        try {
            extensionSet();
            BufferedImage viaImageIO = ImageIO.read(new ByteArrayInputStream(data));
            if (viaImageIO != null) return argb(viaImageIO);
        } catch (IOException | RuntimeException exception) { first = exception instanceof IOException io ? io : new IOException(exception.getMessage(), exception); }
        try {
            BufferedImage own = decodeOwn(data, format);
            if (own != null) return own;
        } catch (RuntimeException exception) {   // a truncated or corrupted file ends up as an index error in a decoder
            throw new IOException("the " + (format == null ? "picture" : format) + " file is damaged or uses an unsupported variant", exception);
        }
        if (first != null && format != null) throw first;
        throw new IOException("unknown picture format");
    }
    //endregion

    //region Sniffing
    private static boolean isUnsupported(String format) {
        for (String name : KNOWN_UNSUPPORTED) if (name.equalsIgnoreCase(format)) return true;
        return false;
    }

    private static String sniff(byte[] b, int n, String name) {
        if (n >= 8 && (b[0] & 255) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "PNG";
        if (n >= 3 && (b[0] & 255) == 0xFF && (b[1] & 255) == 0xD8) return "JPEG";
        if (n >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "GIF";
        if (n >= 2 && b[0] == 'B' && b[1] == 'M') return "BMP";
        if (n >= 4 && ((b[0] == 'I' && b[1] == 'I' && b[2] == 42 && b[3] == 0) || (b[0] == 'M' && b[1] == 'M' && b[2] == 0 && b[3] == 42))) return "TIFF";
        if (n >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "WEBP";
        if (n >= 4 && b[0] == 'q' && b[1] == 'o' && b[2] == 'i' && b[3] == 'f') return "QOI";
        if (n >= 4 && b[0] == '8' && b[1] == 'B' && b[2] == 'P' && b[3] == 'S') return "PSD";
        if (n >= 6 && b[0] == 0 && b[1] == 0 && (b[2] == 1 || b[2] == 2) && b[3] == 0 && (b[4] & 255) >= 1 && b[5] == 0) return b[2] == 1 ? "ICO" : "CUR";   // a TGA header starts the same way, but its bytes 4-5 are zero
        if (n >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            String brand = new String(b, 8, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (brand.startsWith("avif") || brand.startsWith("avis")) return "AVIF";
            if (brand.startsWith("hei") || brand.startsWith("mif1") || brand.startsWith("msf1")) return "HEIC";
        }
        if (n >= 2 && b[0] == 'P' && b[1] >= '1' && b[1] <= '7') return b[1] == '7' ? "PAM" : "PNM";
        if (n >= 4 && (b[0] & 255) == 0xFF && (b[1] & 255) == 0x0A) return "JXL";
        if (n >= 5 && (b[0] == '<' && (b[1] == 's' || b[1] == '?'))) return "SVG";
        if (n >= 4 && (b[0] & 255) == 0x0A && b[1] <= 5 && b[2] == 1) return "PCX";
        if (n >= 18 && (b[1] == 0 || b[1] == 1)) {   // TGA has no magic number: accept a header that is plausible in every field
            int type = b[2] & 255, depth = b[16] & 255, w = (b[12] & 255) | ((b[13] & 255) << 8), h = (b[14] & 255) | ((b[15] & 255) << 8);
            boolean knownType = type == 1 || type == 2 || type == 3 || type == 9 || type == 10 || type == 11;
            boolean knownDepth = depth == 8 || depth == 15 || depth == 16 || depth == 24 || depth == 32;
            if (knownType && knownDepth && w > 0 && h > 0) return "TGA";
        }
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (String ext : new String[]{".tga", ".icb", ".vda", ".vst", ".targa"}) if (lower.endsWith(ext)) return "TGA";
        if (lower.endsWith(".wbmp")) return "WBMP";
        return null;
    }
    //endregion

    //region Dispatch
    private static BufferedImage decodeOwn(byte[] data, String format) throws IOException {
        if (format == null) return null;
        return switch (format) {
            case "TGA" -> decodeTga(data);
            case "PCX" -> decodePcx(data);
            case "ICO", "CUR" -> decodeIco(data);
            case "PNM", "PAM" -> decodePnm(data);
            case "QOI" -> decodeQoi(data);
            case "PSD" -> decodePsd(data);
            default -> null;
        };
    }

    private static BufferedImage argb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) return source;
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[source.getWidth()];
        for (int y = 0; y < source.getHeight(); y++) {
            source.getRGB(0, y, source.getWidth(), 1, row, 0, source.getWidth());
            copy.setRGB(0, y, source.getWidth(), 1, row, 0, source.getWidth());
        }
        return copy;
    }

    private static BufferedImage image(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) throw new IOException("unsupported picture size " + width + " x " + height);
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private static int u8(byte[] d, int i) { return d[i] & 255; }
    private static int le16(byte[] d, int i) { return u8(d, i) | (u8(d, i + 1) << 8); }
    private static int le32(byte[] d, int i) { return le16(d, i) | (le16(d, i + 2) << 16); }
    private static int be16(byte[] d, int i) { return (u8(d, i) << 8) | u8(d, i + 1); }
    private static int be32(byte[] d, int i) { return (be16(d, i) << 16) | be16(d, i + 2); }
    private static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }
    //endregion

    //region TGA
    private static BufferedImage decodeTga(byte[] d) throws IOException {
        if (d.length < 18) throw new IOException("TGA header is truncated");
        int idLength = u8(d, 0), colorMapType = u8(d, 1), type = u8(d, 2);
        int mapStart = le16(d, 3), mapLength = le16(d, 5), mapDepth = u8(d, 7);
        int width = le16(d, 12), height = le16(d, 14), bpp = u8(d, 16), descriptor = u8(d, 17);
        boolean rle = type >= 9;
        int baseType = rle ? type - 8 : type;
        if (baseType != 1 && baseType != 2 && baseType != 3) throw new IOException("unsupported TGA type " + type);
        BufferedImage result = image(width, height);
        int position = 18 + idLength;
        int[] palette = null;
        if (colorMapType == 1) {
            int entryBytes = (mapDepth + 7) / 8;
            palette = new int[mapStart + mapLength];
            for (int i = 0; i < mapLength; i++) palette[mapStart + i] = tgaPixel(d, position + i * entryBytes, mapDepth, null, false);
            position += mapLength * entryBytes;
        }
        if (baseType == 1 && palette == null) throw new IOException("TGA has no palette");
        int pixelBytes = (bpp + 7) / 8;
        if (pixelBytes < 1 || pixelBytes > 4) throw new IOException("unsupported TGA depth " + bpp);
        int[] pixels = new int[width * height];
        int count = 0;
        boolean hasAlpha = (descriptor & 15) > 0 || bpp == 32;
        while (count < pixels.length) {
            if (rle) {
                int packet = u8(d, position++);
                int run = (packet & 127) + 1;
                if ((packet & 128) != 0) {
                    int color = tgaSample(d, position, bpp, baseType, palette, hasAlpha);
                    position += pixelBytes;
                    for (int i = 0; i < run && count < pixels.length; i++) pixels[count++] = color;
                } else {
                    for (int i = 0; i < run && count < pixels.length; i++) {
                        pixels[count++] = tgaSample(d, position, bpp, baseType, palette, hasAlpha);
                        position += pixelBytes;
                    }
                }
            } else {
                pixels[count++] = tgaSample(d, position, bpp, baseType, palette, hasAlpha);
                position += pixelBytes;
            }
        }
        boolean topDown = (descriptor & 0x20) != 0, rightToLeft = (descriptor & 0x10) != 0;
        for (int y = 0; y < height; y++) {
            int sourceRow = topDown ? y : height - 1 - y;
            for (int x = 0; x < width; x++) {
                int sourceX = rightToLeft ? width - 1 - x : x;
                result.setRGB(x, y, pixels[sourceRow * width + sourceX]);
            }
        }
        return result;
    }
    private static int tgaSample(byte[] d, int at, int bpp, int baseType, int[] palette, boolean hasAlpha) {
        if (baseType == 1) {
            int index = bpp == 16 ? le16(d, at) : u8(d, at);
            return palette[Math.min(index, palette.length - 1)];
        }
        if (baseType == 3) {
            if (bpp == 16) { int v = u8(d, at); return argb(u8(d, at + 1), v, v, v); }
            int v = u8(d, at);
            return argb(255, v, v, v);
        }
        return tgaPixel(d, at, bpp, null, hasAlpha);
    }
    private static int tgaPixel(byte[] d, int at, int bpp, int[] unused, boolean hasAlpha) {
        return switch (bpp) {
            case 15, 16 -> {
                int v = le16(d, at);
                int r = ((v >> 10) & 31) * 255 / 31, g = ((v >> 5) & 31) * 255 / 31, b = (v & 31) * 255 / 31;
                yield argb(255, r, g, b);
            }
            case 24 -> argb(255, u8(d, at + 2), u8(d, at + 1), u8(d, at));
            case 32 -> argb(hasAlpha ? u8(d, at + 3) : 255, u8(d, at + 2), u8(d, at + 1), u8(d, at));
            default -> { int v = u8(d, at); yield argb(255, v, v, v); }
        };
    }
    //endregion

    //region PCX
    private static BufferedImage decodePcx(byte[] d) throws IOException {
        if (d.length < 128) throw new IOException("PCX header is truncated");
        int bitsPerPixel = u8(d, 3);
        int width = le16(d, 8) - le16(d, 4) + 1, height = le16(d, 10) - le16(d, 6) + 1;
        int planes = u8(d, 65), bytesPerLine = le16(d, 66);
        BufferedImage result = image(width, height);
        int scan = planes * bytesPerLine;
        byte[] line = new byte[scan];
        int position = 128;
        int[] vga = null;
        if (bitsPerPixel == 8 && planes == 1 && d.length >= 769 && u8(d, d.length - 769) == 12) {
            vga = new int[256];
            for (int i = 0; i < 256; i++) vga[i] = argb(255, u8(d, d.length - 768 + i * 3), u8(d, d.length - 767 + i * 3), u8(d, d.length - 766 + i * 3));
        }
        for (int y = 0; y < height; y++) {
            int filled = 0;
            while (filled < scan) {
                int value = u8(d, position++);
                int run = 1;
                if ((value & 0xC0) == 0xC0) { run = value & 0x3F; value = u8(d, position++); }
                for (int i = 0; i < run && filled < scan; i++) line[filled++] = (byte) value;
            }
            for (int x = 0; x < width; x++) {
                int color;
                if (bitsPerPixel == 8 && planes == 1) {
                    int v = u8(line, x);
                    color = vga != null ? vga[v] : argb(255, v, v, v);
                } else if (bitsPerPixel == 8 && planes >= 3) {
                    int a = planes == 4 ? u8(line, 3 * bytesPerLine + x) : 255;
                    color = argb(a, u8(line, x), u8(line, bytesPerLine + x), u8(line, 2 * bytesPerLine + x));
                } else if (bitsPerPixel == 1) {
                    int index = 0;
                    for (int p = 0; p < planes; p++) index |= ((u8(line, p * bytesPerLine + (x >> 3)) >> (7 - (x & 7))) & 1) << p;
                    if (planes == 1) { int v = index * 255; color = argb(255, v, v, v); }
                    else color = argb(255, u8(d, 16 + index * 3), u8(d, 17 + index * 3), u8(d, 18 + index * 3));
                } else throw new IOException("unsupported PCX variant (" + bitsPerPixel + " bit, " + planes + " planes)");
                result.setRGB(x, y, color);
            }
        }
        return result;
    }
    //endregion

    //region ICO / CUR
    private static BufferedImage decodeIco(byte[] d) throws IOException {
        int count = le16(d, 4);
        if (count < 1 || d.length < 6 + count * 16) throw new IOException("icon directory is damaged");
        int best = -1;
        long bestScore = -1;
        for (int i = 0; i < count; i++) {
            int base = 6 + i * 16;
            int w = u8(d, base) == 0 ? 256 : u8(d, base), h = u8(d, base + 1) == 0 ? 256 : u8(d, base + 1);
            long score = (long) w * h * 1000 + le16(d, base + 6);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        int base = 6 + best * 16;
        int size = le32(d, base + 8), offset = le32(d, base + 12);
        if (offset < 0 || size <= 0 || offset + size > d.length) throw new IOException("icon data is truncated");
        byte[] entry = java.util.Arrays.copyOfRange(d, offset, offset + size);
        if (entry.length > 8 && (entry[0] & 255) == 0x89 && entry[1] == 'P') {
            BufferedImage png = ImageIO.read(new ByteArrayInputStream(entry));
            if (png == null) throw new IOException("icon PNG is damaged");
            return argb(png);
        }
        return decodeDib(entry, true);
    }
    private static BufferedImage decodeDib(byte[] d, boolean withMask) throws IOException {
        int headerSize = le32(d, 0);
        int width = le32(d, 4), rawHeight = le32(d, 8), bpp = le16(d, 14), compression = le32(d, 16);
        int colorsUsed = le32(d, 32);
        if (compression != 0 && compression != 3) throw new IOException("compressed icon bitmaps are not supported");
        int height = withMask ? rawHeight / 2 : Math.abs(rawHeight);
        BufferedImage result = image(width, height);
        int paletteSize = bpp <= 8 ? (colorsUsed != 0 ? colorsUsed : 1 << bpp) : 0;
        int pixelsAt = headerSize + paletteSize * 4;
        int stride = ((width * bpp + 31) / 32) * 4;
        int maskStride = ((width + 31) / 32) * 4;
        int maskAt = pixelsAt + stride * height;
        boolean anyAlpha = false;
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = pixelsAt + (height - 1 - y) * stride;
            for (int x = 0; x < width; x++) {
                int color;
                switch (bpp) {
                    case 32 -> { int at = row + x * 4; color = argb(u8(d, at + 3), u8(d, at + 2), u8(d, at + 1), u8(d, at)); if (u8(d, at + 3) != 0) anyAlpha = true; }
                    case 24 -> { int at = row + x * 3; color = argb(255, u8(d, at + 2), u8(d, at + 1), u8(d, at)); }
                    case 8 -> color = paletteColor(d, headerSize, u8(d, row + x), paletteSize);
                    case 4 -> color = paletteColor(d, headerSize, (u8(d, row + (x >> 1)) >> ((x & 1) == 0 ? 4 : 0)) & 15, paletteSize);
                    case 1 -> color = paletteColor(d, headerSize, (u8(d, row + (x >> 3)) >> (7 - (x & 7))) & 1, paletteSize);
                    default -> throw new IOException("unsupported icon depth " + bpp);
                }
                argb[y * width + x] = color;
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = argb[y * width + x];
                if (withMask && !(bpp == 32 && anyAlpha) && maskAt + maskStride * height <= d.length) {
                    int maskRow = maskAt + (height - 1 - y) * maskStride;
                    boolean transparent = ((u8(d, maskRow + (x >> 3)) >> (7 - (x & 7))) & 1) != 0;
                    color = transparent ? color & 0x00FFFFFF : color | 0xFF000000;
                }
                result.setRGB(x, y, color);
            }
        }
        return result;
    }
    private static int paletteColor(byte[] d, int paletteAt, int index, int size) {
        if (index >= size) return 0xFF000000;
        int at = paletteAt + index * 4;
        return argb(255, u8(d, at + 2), u8(d, at + 1), u8(d, at));
    }
    //endregion

    //region PNM / PAM
    private static BufferedImage decodePnm(byte[] d) throws IOException {
        int[] cursor = {2};
        int kind = d[1] - '0';
        int width, height, maxValue = 1, depth;
        boolean pamHasAlpha = false;
        if (kind == 7) {
            width = height = 0; maxValue = 255; depth = 3;
            String header = new String(d, 0, Math.min(d.length, 512), java.nio.charset.StandardCharsets.ISO_8859_1);
            int end = header.indexOf("ENDHDR");
            if (end < 0) throw new IOException("PAM header is damaged");
            for (String line : header.substring(2, end).split("\n")) {
                String[] part = line.trim().split("\\s+");
                if (part.length < 2) continue;
                switch (part[0]) {
                    case "WIDTH" -> width = Integer.parseInt(part[1]);
                    case "HEIGHT" -> height = Integer.parseInt(part[1]);
                    case "DEPTH" -> depth = Integer.parseInt(part[1]);
                    case "MAXVAL" -> maxValue = Integer.parseInt(part[1]);
                    case "TUPLTYPE" -> pamHasAlpha |= part[1].endsWith("_ALPHA");
                    default -> { }
                }
            }
            cursor[0] = end + "ENDHDR".length() + 1;
        } else {
            width = pnmNumber(d, cursor);
            height = pnmNumber(d, cursor);
            if (kind != 1 && kind != 4) maxValue = pnmNumber(d, cursor);
            depth = (kind == 3 || kind == 6) ? 3 : 1;
        }
        if (maxValue < 1 || maxValue > 65535) throw new IOException("bad PNM maximum value");
        BufferedImage result = image(width, height);
        boolean ascii = kind >= 1 && kind <= 3;
        int bytesPerSample = maxValue > 255 ? 2 : 1;
        int[] at = {ascii || kind == 7 ? cursor[0] : skipOneWhitespace(d, cursor[0])};
        int[] sample = new int[Math.max(depth, 1)];
        for (int y = 0; y < height; y++) {
            int rowStart = at[0];
            for (int x = 0; x < width; x++) {
                if (kind == 4) {
                    int v = ((u8(d, rowStart + (x >> 3)) >> (7 - (x & 7))) & 1) == 1 ? 0 : 255;
                    result.setRGB(x, y, argb(255, v, v, v));
                    continue;
                }
                for (int c = 0; c < sample.length; c++) {
                    int raw;
                    if (kind == 1) raw = pnmBit(d, at);
                    else if (ascii) raw = pnmNumber(d, at);
                    else if (bytesPerSample == 2) { raw = be16(d, at[0]); at[0] += 2; }
                    else raw = u8(d, at[0]++);
                    sample[c] = kind == 1 ? (raw == 1 ? 0 : 255) : Math.min(255, raw * 255 / maxValue);
                }
                int r = sample[0], g = depth >= 3 ? sample[1] : sample[0], b = depth >= 3 ? sample[2] : sample[0];
                int a = (kind == 7 && pamHasAlpha) ? sample[depth - 1] : 255;
                result.setRGB(x, y, argb(a, r, g, b));
            }
            if (kind == 4) at[0] = rowStart + (width + 7) / 8;
        }
        return result;
    }
    private static int pnmBit(byte[] d, int[] at) throws IOException {
        int i = at[0];
        while (i < d.length && (Character.isWhitespace(d[i]) || d[i] == '#'))
            if (d[i] == '#') { while (i < d.length && d[i] != '\n') i++; } else i++;
        if (i >= d.length || (d[i] != '0' && d[i] != '1')) throw new IOException("bad PBM data");
        at[0] = i + 1;
        return d[i] - '0';
    }
    private static int skipOneWhitespace(byte[] d, int at) { return at < d.length && Character.isWhitespace(d[at]) ? at + 1 : at; }
    private static int pnmNumber(byte[] d, int[] cursor) throws IOException {
        int i = cursor[0];
        while (i < d.length) {
            if (d[i] == '#') { while (i < d.length && d[i] != '\n') i++; }
            else if (Character.isWhitespace(d[i])) i++;
            else break;
        }
        int value = 0, digits = 0;
        while (i < d.length && d[i] >= '0' && d[i] <= '9') { value = value * 10 + (d[i] - '0'); i++; digits++; }
        if (digits == 0) throw new IOException("bad PNM number");
        cursor[0] = i;
        return value;
    }
    //endregion

    //region QOI
    private static BufferedImage decodeQoi(byte[] d) throws IOException {
        if (d.length < 22) throw new IOException("QOI file is truncated");
        int width = be32(d, 4), height = be32(d, 8);
        BufferedImage result = image(width, height);
        int[] seen = new int[64];
        int r = 0, g = 0, b = 0, a = 255, position = 14, run = 0;
        int total = width * height;
        int[] pixels = new int[total];
        for (int i = 0; i < total; i++) {
            if (run > 0) run--;
            else if (position < d.length - 8) {
                int op = u8(d, position++);
                if (op == 0xFE) { r = u8(d, position++); g = u8(d, position++); b = u8(d, position++); }
                else if (op == 0xFF) { r = u8(d, position++); g = u8(d, position++); b = u8(d, position++); a = u8(d, position++); }
                else switch (op & 0xC0) {
                    case 0x00 -> { int c = seen[op]; a = c >>> 24; r = (c >> 16) & 255; g = (c >> 8) & 255; b = c & 255; }
                    case 0x40 -> { r = (r + ((op >> 4) & 3) - 2) & 255; g = (g + ((op >> 2) & 3) - 2) & 255; b = (b + (op & 3) - 2) & 255; }
                    case 0x80 -> {
                        int next = u8(d, position++);
                        int dg = (op & 63) - 32;
                        r = (r + dg - 8 + ((next >> 4) & 15)) & 255;
                        g = (g + dg) & 255;
                        b = (b + dg - 8 + (next & 15)) & 255;
                    }
                    default -> run = op & 63;
                }
                seen[(r * 3 + g * 5 + b * 7 + a * 11) & 63] = argb(a, r, g, b);
            }
            pixels[i] = argb(a, r, g, b);
        }
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }
    //endregion

    //region PSD
    private static BufferedImage decodePsd(byte[] d) throws IOException {
        int channels = be16(d, 12), height = be32(d, 14), width = be32(d, 18), depth = be16(d, 22), mode = be16(d, 24);
        if (depth != 8 || (mode != 3 && mode != 1)) throw new IOException("only 8-bit RGB or grayscale PSD files are supported");
        BufferedImage result = image(width, height);
        int position = 26;
        position += 4 + be32(d, position);
        position += 4 + be32(d, position);
        position += 4 + be32(d, position);
        int compression = be16(d, position);
        position += 2;
        int planeSize = width * height;
        int usable = Math.min(channels, mode == 3 ? 4 : 2);
        byte[][] planes = new byte[usable][planeSize];
        if (compression == 0) {
            for (int c = 0; c < usable; c++) System.arraycopy(d, position + c * planeSize, planes[c], 0, planeSize);
        } else if (compression == 1) {
            int[] rowLengths = new int[channels * height];
            for (int i = 0; i < rowLengths.length; i++) rowLengths[i] = be16(d, position + i * 2);
            position += rowLengths.length * 2;
            for (int c = 0; c < channels; c++) {
                for (int y = 0; y < height; y++) {
                    int end = position + rowLengths[c * height + y];
                    int x = 0;
                    while (position < end && x < width) {
                        int n = d[position++];
                        if (n >= 0) { for (int i = 0; i <= n && x < width; i++) { byte v = d[position++]; if (c < usable) planes[c][y * width + x] = v; x++; } }
                        else if (n != -128) { byte v = d[position++]; for (int i = 0; i < 1 - n && x < width; i++) { if (c < usable) planes[c][y * width + x] = v; x++; } }
                    }
                    position = end;
                }
            }
        } else throw new IOException("unsupported PSD compression " + compression);
        for (int i = 0; i < planeSize; i++) {
            int r = planes[0][i] & 255, g = mode == 3 ? planes[1][i] & 255 : r, b = mode == 3 ? planes[2][i] & 255 : r;
            int a = mode == 3 ? (usable == 4 ? planes[3][i] & 255 : 255) : (usable == 2 ? planes[1][i] & 255 : 255);
            result.setRGB(i % width, i / width, argb(a, r, g, b));
        }
        return result;
    }
    //endregion
}