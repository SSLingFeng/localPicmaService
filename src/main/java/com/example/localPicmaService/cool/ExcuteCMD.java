//package com.example.localPicmaService.cool;
//
//
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.nio.charset.Charset;
//import java.util.function.Consumer;
//public class ExcuteCMD {
//
//
//    public String command;
//
//    public String getCommand() {
//        return command;
//    }
//
//    public void setCommand(String command) {
//        this.command = command;
//    }
//
//    /**
//     * 执行系统命令，并把每一行输出交给回调
//     */
//    public static void execute(String command, Consumer<String> onLine) {
//        try {
//            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
//            pb.redirectErrorStream(true);
//            Process process = pb.start();
//
//            try (BufferedReader reader =
//                         new BufferedReader(new InputStreamReader(
//                                 process.getInputStream(),
//                                 Charset.forName("GBK")))) {
//
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    onLine.accept(line);
//                }
//            }
//
//            process.waitFor();
//        } catch (Exception e) {
//            onLine.accept("[ERROR] " + e.getMessage());
//        }
//    }
//}
