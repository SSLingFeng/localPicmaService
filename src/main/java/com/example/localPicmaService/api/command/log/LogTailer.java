package com.example.localPicmaService.api.command.log;

import com.example.localPicmaService.config.SystemConfig;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class LogTailer {

    public static void tail(Path logDir, LogLineConsumer consumer) throws Exception {
        Path currentFile = null;
        long filePointer = 0;
        while (true) {
            Path latest = findLatestLogFile(logDir);
            if (latest == null) {
                Thread.sleep(1000);
                continue;
            }
            if (!latest.equals(currentFile)) {
                currentFile = latest;
                filePointer = 0;
            }
            filePointer = readNewLines(currentFile, filePointer, consumer);
            Thread.sleep(500);
        }
    }

    private static Path findLatestLogFile(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".log"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElse(null);
        }
    }

    private static long readNewLines(Path file, long pointer, LogLineConsumer consumer) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(pointer);
            String line;
            while ((line = raf.readLine()) != null) {
                String decoded = new String(
                        line.getBytes(StandardCharsets.ISO_8859_1),
                        Charset.defaultCharset());
                consumer.onLine(decoded);
            }
            return raf.getFilePointer();
        }
    }
}
