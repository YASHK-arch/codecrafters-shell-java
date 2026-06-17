import java.util.Scanner;
import java.util.*;
import java.io.*;

public class Main {

    private static File findExecutable(String commandName) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        String[] pathDirs = pathEnv.split(File.pathSeparator);

        for (String dir : pathDirs) {
            File file = new File(dir, commandName);

            if (file.exists() && file.canExecute()) {
                return file;
            }
        }

        return null;
    }

    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        HashSet<String> builtins = new HashSet<>();
        builtins.add("echo");
        builtins.add("type");
        builtins.add("exit");

        while (true) {
            System.out.print("$ ");

            String command = sc.nextLine();

            if (command.equals("exit")) {
                break;
            }

            else if (command.startsWith("echo ")) {
                String message = command.substring(5);
                System.out.println(message);
            }

            else if (command.startsWith("type ")) {
                String commandName = command.substring(5).trim();

                if (builtins.contains(commandName)) {
                    System.out.println(commandName + " is a shell builtin");
                    continue;
                }

                File executable = findExecutable(commandName);

                if (executable != null) {
                    System.out.println(
                        commandName + " is " + executable.getAbsolutePath()
                    );
                } else {
                    System.out.println(commandName + ": not found");
                }
            }

            else {

                String[] parts = command.trim().split("\\s+");

                String commandName = parts[0];

                File executable = findExecutable(commandName);

                if (executable == null) {
                    System.out.println(commandName + ": command not found");
                    continue;
                }

                List<String> processCommand = new ArrayList<>();

                processCommand.add(executable.getAbsolutePath());

                for (int i = 1; i < parts.length; i++) {
                    processCommand.add(parts[i]);
                }

                ProcessBuilder pb = new ProcessBuilder(processCommand);

                pb.redirectErrorStream(true);

                Process process = pb.start();

                BufferedReader reader =
                    new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                    );

                String line;

                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }

                process.waitFor();
            }
        }

        sc.close();
    }
}