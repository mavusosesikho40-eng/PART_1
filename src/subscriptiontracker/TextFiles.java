package subscriptiontracker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads text files the way people actually save them.
 *
 * <p>Files are read as UTF-8. If they aren't valid UTF-8 they are read with
 * the Windows character set instead, which is what Notepad ("ANSI") and
 * Excel ("CSV (Comma delimited)") use by default on Windows, so accented
 * letters and symbols like "€" come through. A UTF-8 byte order mark at the
 * start, which Notepad adds, is removed.
 */
final class TextFiles {

    private static final Charset WINDOWS = Charset.forName("windows-1252");

    private TextFiles() {
    }

    static String read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException notUtf8) {
            text = new String(bytes, WINDOWS);
        }
        return text.startsWith("﻿") ? text.substring(1) : text;
    }
}
