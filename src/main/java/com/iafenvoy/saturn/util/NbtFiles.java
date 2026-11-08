package com.iafenvoy.saturn.util;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * File framing is independent of FileAccess initialization; readers accept both compression modes.
 */
final class NbtFiles {
    private NbtFiles() {
    }

    static Tag read(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        // GZIP magic cannot be a valid NBT root type, so compression can be detected without a failing parse.
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B) {
            try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                bytes = input.readAllBytes();
            }
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return NbtIo.readAnyTag(input, NbtAccounter.unlimitedHeap());
        }
    }

    static void write(Tag tag, OutputStream stream, boolean compressed) throws IOException {
        try (DataOutputStream output = new DataOutputStream(compressed ? new GZIPOutputStream(stream) : stream)) {
            NbtIo.writeAnyTag(tag, output);
        }
    }
}