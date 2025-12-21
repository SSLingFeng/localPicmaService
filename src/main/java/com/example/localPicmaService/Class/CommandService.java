package com.example.localPicmaService.Class;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.function.Consumer;

@Service
public class CommandService {

    public void execute(String command, Consumer<String> onLine) {
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            process.getInputStream(),
                            Charset.forName("GBK")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    onLine.accept(line);
                }
            }
            process.waitFor();
        } catch (Exception e) {
            onLine.accept("[ERROR] " + e.getMessage());
        }
    }
}
