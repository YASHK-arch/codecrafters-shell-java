import java.util.*;
import java.io.*;

public class Main {

    // Input PARSER
    private static List<String> parseCommand(String input) {
        List<String> tokens = new ArrayList<>();

        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (ch == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if (ch == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if (Character.isWhitespace(ch) &&
                    !inSingleQuotes &&
                    !inDoubleQuotes) {

                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }

            } else {
                current.append(ch);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    // Checks Executability of the file and whether it exists or not
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

        Set<String> builtins = new HashSet<>();
        builtins.add("echo");
        builtins.add("type");
        builtins.add("exit");
        builtins.add("pwd");
        builtins.add("cd");

        File currentDir = new File(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");

            String command = sc.nextLine();

            if (command.equals("exit")) {
                break;
            }

            else if (command.startsWith("echo ")) {
                List<String> parts = parseCommand(command);

                for (int i = 1; i < parts.size(); i++) {

                    if (i > 1) {
                        System.out.print(" ");
                    }

                    System.out.print(parts.get(i));
                }

                System.out.println();
            }

            else if (command.equals("pwd")) {
                System.out.println(currentDir.getAbsolutePath());
            }

            else if (command.startsWith("cd ")) {

                String dirPath = command.substring(3).trim();

                File newDir;

                if (dirPath.equals("~")) {
                    newDir = new File(System.getenv("HOME"));
                } else if (dirPath.startsWith("/")) {
                    newDir = new File(dirPath);
                } else {
                    newDir = new File(currentDir, dirPath);
                }

                if (newDir.exists() && newDir.isDirectory()) {
                    currentDir = newDir.getCanonicalFile();
                } else {
                    System.out.println(
                            "cd: " + dirPath + ": No such file or directory");
                }
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
                            commandName + " is " + executable.getAbsolutePath());
                } else {
                    System.out.println(commandName + ": not found");
                }
            }

            else {

                List<String> parts = parseCommand(command);
                String commandName = parts.get(0);
                File executable = findExecutable(commandName);

                if (executable == null) {
                    System.out.println(commandName + ": command not found");
                    continue;
                }

                List<String> processCommand = new ArrayList<>(parts);

                ProcessBuilder pb = new ProcessBuilder(processCommand);

                pb.redirectErrorStream(true);

                Process process = pb.start();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                process.getInputStream()));

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