import java.util.*;
import java.io.*;

public class Main {

    private static File findExecutable(String commandName) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv != null) {
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

        // Define builtins once
        HashSet<String> builtin = new HashSet<>();
        builtin.add("echo");
        builtin.add("type");
        builtin.add("exit");

        while (true) {
            System.out.print("$ ");
            String command = sc.nextLine();

            if (command.equals("exit")) {
                break;

            } else if (command.startsWith("echo ")) {
                String commandName = command.substring(5);
                System.out.println(commandName);

            } else if (command.startsWith("type ")) {
                String commandName = command.substring(5).trim();
                // Step 1: check builtins
                if (builtin.contains(commandName)) {
                    System.out.println(commandName + " is a shell builtin");
                    continue;
                }

                File executable = findExecutable(commandName);

                if (executable != null) {
                    System.out.println(
                            commandName + " is " + executable.getAbsolutePath());
                } else {
                    System.out.println(commandName + ": not found");
                }
            } else {
                String[] parts = command.trim().split("\\s+");
                String commandName = parts[0];
                File executable = findExecutable(commandName);

                if (executable == null) {
                    System.out.println(commandName + ": command not found");
                    continue;
                }

                ArrayList<String> processCommand = new ArrayList<>();
                processCommand.add(executable.getAbsolutePath());
                for (int i = 1; i < parts.length; i++) {
                    processCommand.add(parts[i]);
                }

                ProcessBuilder pb = new ProcessBuilder(processCommand);
                pb.redirectErrorStream(true);

                Process process = pb.start();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));

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
