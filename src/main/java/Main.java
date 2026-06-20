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

            // Backslashes inside double quotes
            if (ch == '\\' && inDoubleQuotes) {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                        continue;
                    }
                }
                current.append('\\');
                continue;
            }
            // Backslash escaping outside quotes
            if (ch == '\\' && !inSingleQuotes && !inDoubleQuotes) {
                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++; // skip escaped character
                }
                continue;
            }

            if (ch == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if (ch == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if (!inSingleQuotes && !inDoubleQuotes) {
                if (ch == '>') {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    tokens.add(">");
                    continue;
                }
                if (ch == '1' && i + 1 < input.length() && input.charAt(i + 1) == '>') {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    tokens.add("1>");
                    i++;
                    continue;
                }
            }

            if (Character.isWhitespace(ch) && !inSingleQuotes && !inDoubleQuotes) {
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
        String[] pathDirs = pathEnv.split(File.separator.equals(":") ? ":" : File.pathSeparator);
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

            List<String> parts = parseCommand(command);

            // Handle redirection
            String outputFile = null;
            int redirectIndex = -1;
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).equals(">") || parts.get(i).equals("1>")) {
                    redirectIndex = i;
                    if (i + 1 < parts.size()) {
                        outputFile = parts.get(i + 1);
                    }
                    break;
                }
            }

            PrintStream out = System.out;
            if (outputFile != null) {
                out = new PrintStream(new FileOutputStream(outputFile));
            }

            // Trim command parts before redirection
            if (redirectIndex != -1) {
                parts = new ArrayList<>(parts.subList(0, redirectIndex));
            }

            if (parts.isEmpty()) continue;

            String cmd = parts.get(0);

            if (cmd.equals("exit")) {
                break;
            }

            else if (cmd.equals("echo")) {
                for (int i = 1; i < parts.size(); i++) {
                    if (i > 1) out.print(" ");
                    out.print(parts.get(i));
                }
                out.println();
            }

            else if (cmd.equals("pwd")) {
                out.println(currentDir.getAbsolutePath());
            }

            else if (cmd.equals("cd")) {
                if (parts.size() < 2) {
                    out.println("cd: missing operand");
                } else {
                    String dirPath = parts.get(1);
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
                        out.println("cd: " + dirPath + ": No such file or directory");
                    }
                }
            }

            else if (cmd.equals("type")) {
                if (parts.size() < 2) {
                    out.println("type: missing operand");
                } else {
                    String commandName = parts.get(1);
                    if (builtins.contains(commandName)) {
                        out.println(commandName + " is a shell builtin");
                    } else {
                        File executable = findExecutable(commandName);
                        if (executable != null) {
                            out.println(commandName + " is " + executable.getAbsolutePath());
                        } else {
                            out.println(commandName + ": not found");
                        }
                    }
                }
            }

            else {
                File executable = findExecutable(cmd);
                if (executable == null) {
                    out.println(cmd + ": command not found");
                } else {
                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.directory(currentDir);
                    if (outputFile != null) {
                        pb.redirectOutput(new File(outputFile));
                    }
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                    Process process = pb.start();

                    if (outputFile == null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                    }

                    process.waitFor();
                }
            }

            if (out != System.out) {
                out.close();
            }
        }

        sc.close();
    }
}
