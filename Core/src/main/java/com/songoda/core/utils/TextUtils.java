package com.songoda.core.utils;

import org.bukkit.ChatColor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TextUtils {
    private static final List<Charset> supportedCharsets = new ArrayList<>();

    static {
        supportedCharsets.add(StandardCharsets.UTF_8); // UTF-8 BOM: EF BB BF
        supportedCharsets.add(StandardCharsets.ISO_8859_1); // also starts with EF BB BF
//        supportedCharsets.add(StandardCharsets.UTF_16LE); // FF FE
//        supportedCharsets.add(StandardCharsets.UTF_16BE); // FE FF
//        supportedCharsets.add(StandardCharsets.UTF_16);

        try {
            supportedCharsets.add(Charset.forName("windows-1253"));
            supportedCharsets.add(Charset.forName("ISO-8859-7"));
        } catch (Exception ignore) {    // UnsupportedCharsetException technically can be thrown, but can also be ignored
        }

        supportedCharsets.add(StandardCharsets.US_ASCII);
    }

    public static String formatText(String text) {
        return formatText(text, false);
    }

    public static String formatText(String text, boolean capitalize) {
        if (text == null || text.equals(""))
            return "";
        if (capitalize)
            text = text.substring(0, 1).toUpperCase() + text.substring(1);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> formatText(List<String> list) {
        return list.stream().map(TextUtils::formatText).collect(Collectors.toList());
    }

    public static List<String> formatText(String... list) {
        return Arrays.stream(list).map(TextUtils::formatText).collect(Collectors.toList());
    }

    public static List<String> wrap(String line) {
        return wrap(null, line);
    }

    public static List<String> wrap(String color, String line) {
        if (color != null)
            color = "&" + color;
        else
            color = "";

        List<String> lore = new ArrayList<>();
        int lastIndex = 0;
        for (int n = 0; n < line.length(); n++) {
            if (n - lastIndex < 25)
                continue;

            if (line.charAt(n) == ' ') {
                lore.add(TextUtils.formatText(color + TextUtils.formatText(line.substring(lastIndex, n))));
                lastIndex = n;
            }
        }

        if (lastIndex - line.length() < 25)
            lore.add(TextUtils.formatText(color + TextUtils.formatText(line.substring(lastIndex))));
        return lore;
    }

    /**
     * Convert a string to an invisible colored string that's lore-safe <br />
     * (Safe to use as lore) <br />
     * Note: Do not use semi-colons in this string, or they will be lost when decoding!
     *
     * @param s string to convert
     * @return encoded string
     */
    public static String convertToInvisibleLoreString(String s) {
        if (s == null || s.equals(""))
            return "";
        StringBuilder hidden = new StringBuilder();
        for (char c : s.toCharArray())
            hidden.append(ChatColor.COLOR_CHAR).append(';').append(ChatColor.COLOR_CHAR).append(c);
        return hidden.toString();
    }

    /**
     * Convert a string to an invisible colored string <br />
     * (Not safe to use as lore) <br />
     * Note: Do not use semi-colons in this string, or they will be lost when decoding!
     *
     * @param s string to convert
     * @return encoded string
     */
    public static String convertToInvisibleString(String s) {
        if (s == null || s.equals(""))
            return "";
        StringBuilder hidden = new StringBuilder();
        for (char c : s.toCharArray()) hidden.append(ChatColor.COLOR_CHAR).append(c);
        return hidden.toString();
    }

    /**
     * Removes color markers used to encode strings as invisible text
     *
     * @param s encoded string
     * @return string with color markers removed
     */
    public static String convertFromInvisibleString(String s) {
        if (s == null || s.equals("")) {
            return "";
        }
        return s.replaceAll(ChatColor.COLOR_CHAR + ";" + ChatColor.COLOR_CHAR + "|" + ChatColor.COLOR_CHAR, "");
    }

    public static Charset detectCharset(File f, Charset def) {
        byte[] buffer = new byte[2048];
        int len;

        // Read the first 2KiB of the file and test the file's encoding
        try (FileInputStream input = new FileInputStream(f)) {
            len = input.read(buffer);
        } catch (Exception ex) {
            return null;
        }

        return len != -1 ? detectCharset(buffer, len, def) : def;
    }

    public static Charset detectCharset(BufferedInputStream reader, Charset def) {
        byte[] buffer = new byte[2048];
        int len;

        // Read the first 2KiB of the file and test the file's encoding
        try {
            reader.mark(2048);
            len = reader.read(buffer);
            reader.reset();
        } catch (Exception ex) {
            return null;
        }

        return len != -1 ? detectCharset(buffer, len, def) : def;
    }

    public static Charset detectCharset(byte[] data, int len, Charset def) {
        // check the file header
        if (len > 4) {
            if (data[0] == (byte) 0xFF && data[1] == (byte) 0xFE) { // FF FE 00 00 is UTF-32LE
                return StandardCharsets.UTF_16LE;
            } else if (data[0] == (byte) 0xFE && data[1] == (byte) 0xFF) {  // 00 00 FE FF is UTF-32BE
                return StandardCharsets.UTF_16BE;
            } else if (data[0] == (byte) 0xEF && data[1] == (byte) 0xBB && data[2] == (byte) 0xBF) { // UTF-8 with BOM, same sig as ISO-8859-1
                return StandardCharsets.UTF_8;
            }
        }

        // Look for last Whitespace Character and ignore potentially broken words/multi-byte characters
        int newLen = len;
        for (; newLen > 0; --newLen) {
            if (Character.isWhitespace(data[newLen - 1])) break;
        }

        // Buffer got too small? => checking whole buffer
        if (len > 512 && newLen < 512) {
            newLen = len;
        }

        ByteBuffer bBuff = ByteBuffer.wrap(data, 0, newLen).asReadOnlyBuffer();

        // Check through a list of charsets and return the first one that could decode the buffer
        for (Charset charset : supportedCharsets) {
            if (charset != null && isCharset(bBuff, charset)) {
                return charset;
            }

            bBuff.rewind();
        }

        return def;
    }

    public static boolean isCharset(ByteBuffer data, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        return decoder.decode(data, CharBuffer.allocate(data.capacity()), true).isUnderflow();
    }
}