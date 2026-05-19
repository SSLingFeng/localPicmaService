package com.example.localPicmaService.Class.Log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class LogTailer {

    /**
     * 启动日志监听（阻塞方法）
     *
     * @param logDir   单个 task 的日志目录
     * @param consumer 每读到一行就回调
     */
    public static void tail(Path logDir, LogLineConsumer consumer)
            throws Exception {

        Path currentFile = null;
        long filePointer = 0;

        while (true) {

            // 1️⃣ 找到最新的日志文件
            Path latest = findLatestLogFile(logDir);

            if (latest == null) {
                Thread.sleep(1000);
                continue;
            }

            // 2️⃣ 如果切换了新文件，重置指针
            if (!latest.equals(currentFile)) {
                currentFile = latest;
                filePointer = 0;
            }

            // 3️⃣ 读取新增内容
            filePointer = readNewLines(
                    currentFile,
                    filePointer,
                    consumer
            );

            Thread.sleep(500); // 控制轮询频率
        }
    }

    private static Path findLatestLogFile(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".log"))
                    .max(Comparator.comparingLong(
                            p -> p.toFile().lastModified()))
                    .orElse(null);
        }
    }


    private static long readNewLines(
            Path file,
            long pointer,
            LogLineConsumer consumer
    ) throws Exception {

        try (RandomAccessFile raf =
                     new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(pointer);

            String line;
            while ((line = raf.readLine()) != null) {

                // 处理 Windows/Linux 编码问题
                String decoded = new String(
                        line.getBytes(StandardCharsets.ISO_8859_1),
                        Charset.defaultCharset()
                );

                consumer.onLine(decoded);
            }

            return raf.getFilePointer();
        }
    }

}
