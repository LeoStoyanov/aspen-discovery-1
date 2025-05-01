package com.turning_leaf_technologies.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressionUtils {

    /**
     * Compresses a String using GZIP compression
     *
     * @param data The string to compress
     * @return The compressed bytes
     * @throws IOException If compression fails
     */
    public static byte[] compress(String data) throws IOException {
        if (data == null || data.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(data.getBytes(StandardCharsets.UTF_8));
        }

        return outputStream.toByteArray();
    }

    /**
     * Decompresses GZIP compressed data back to a String
     *
     * @param compressedData The compressed bytes
     * @return The decompressed string
     * @throws IOException If decompression fails
     */
    public static String decompress(byte[] compressedData) throws IOException {
        if (compressedData == null || compressedData.length == 0) {
            return "";
        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedData);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipInputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
        }

        return outputStream.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * Determines if the given byte array is likely GZIP compressed
     *
     * @param data The data to check
     * @return true if the data appears to be GZIP compressed
     */
    public static boolean isCompressed(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }

        // Check for the GZIP magic number
        return (data[0] == (byte) 0x1f && data[1] == (byte) 0x8b);
    }
}
