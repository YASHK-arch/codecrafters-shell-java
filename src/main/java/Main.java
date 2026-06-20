import java.util.*;
import java.io.*;

public class Main {

    // Job class to store background job information
    static class Job {
        int jobNumber;
        long pid;
        String command;
        String status;

        Job(int jobNumber, long pid, String command, String status) {
            this.jobNumber = jobNumber;
            this.pid = pid;
            this.command = command;
            this.status = status;
        }
    }

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
                // Handle redirection operators
                if (ch == '>') {
                    if (i > 0 && input.charAt(i - 1) == '>') {
                        continue; // already handled as >>
                    }
                    if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                        if (current.length() > 0) {
                            tokens.add(current.toString());
                            current.setLength(0);
                        }
                        tokens.add(">>");
                        i++;
                        continue;
                    } else {
                        if (current.length() > 0) {
                            tokens.add(current.toString());
                            current.setLength(0);
                        }
                        tokens.add(">");
                        continue;
                    }
                }
                if (ch == '1' && i + 1 < input.length()) {
                    if (input.charAt(i + 1) == '>') {
                        if (i + 2 < input.length() && input.charAt(i + 2) == '>') {
                            tokens.add("1>>");
                            i += 2;
                            continue;
                        } else {
                            tokens.add("1>");
                            i++;
                            continue;
                        }
                    }
                }
                if (ch == '2' && i + 1 < input.length()) {
                    if (input.charAt(i + 1) == '>') {
                        if (i + 2 < input.length() && input.charAt(i + 2) == '>') {
                            tokens.add("2>>");
                            i += 2;
                            continue;
                        } else {
                            tokens.add("2>");
                            i++;
                            continue;
                        }
                    }
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
        builtins.add("jobs");

        File currentDir = new File(System.getProperty("user.dir"));
        int jobCounter = 0;
        List<Job> jobs = new ArrayList<>();

        while (true) {
            System.out.print("$ ");
            String command = sc.nextLine();

            List<String> parts = parseCommand(command);

            // Check for background execution (&)
            boolean isBackground = false;
            if (!parts.isEmpty() && parts.get(parts.size() - 1).equals("&")) {
                isBackground = true;
                parts.remove(parts.size() - 1);
            }

            // Save command for jobs list (before redirection trimming)
            String commandStr = String.join(" ", parts);

            // Handle redirection
            String outputFile = null;
            String errorFile = null;
            boolean appendOut = false;
            boolean appendErr = false;
            int redirectIndex = -1;

            for (int i = 0; i < parts.size(); i++) {
                String token = parts.get(i);
                if ((token.equals(">") || token.equals("1>")) && i + 1 < parts.size()) {
                    redirectIndex = i;
                    outputFile = parts.get(i + 1);
                    appendOut = false;
                    break;
                }
                if ((token.equals(">>") || token.equals("1>>")) && i + 1 < parts.size()) {
                    redirectIndex = i;
                    outputFile = parts.get(i + 1);
                    appendOut = true;
                    break;
                }
                if (token.equals("2>") && i + 1 < parts.size()) {
                    redirectIndex = i;
                    errorFile = parts.get(i + 1);
                    appendErr = false;
                    break;
                }
                if (token.equals("2>>") && i + 1 < parts.size()) {
                    redirectIndex = i;
                    errorFile = parts.get(i + 1);
                    appendErr = true;
                    break;
                }
            }

            PrintStream out = System.out;
            PrintStream err = System.err;

            if (outputFile != null) {
                out = new PrintStream(new FileOutputStream(outputFile, appendOut));
            }
            if (errorFile != null) {
                err = new PrintStream(new FileOutputStream(errorFile, appendErr));
            }

            // Trim command parts before redirection
            if (redirectIndex != -1) {
                parts = new ArrayList<>(parts.subList(0, redirectIndex));
            }

            if (parts.isEmpty())
                continue;

            String cmd = parts.get(0);

            if (cmd.equals("exit")) {
                break;
            }

            else if (cmd.equals("echo")) {
                for (int i = 1; i < parts.size(); i++) {
                    if (i > 1)
                        out.print(" ");
                    out.print(parts.get(i));
                }
                out.println();
            }

            else if (cmd.equals("pwd")) {
                out.println(currentDir.getAbsolutePath());
            }

            else if (cmd.equals("cd")) {
                if (parts.size() < 2) {
                    err.println("cd: missing operand");
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
                        err.println("cd: " + dirPath + ": No such file or directory");
                    }
                }
            } else if (cmd.equals("jobs")) {
                for (Job job : jobs) {
                    // Format: [1]+ Running sleep 10 &
                    String statusPadded = String.format("%-24s", job.status);
                    out.println("[" + job.jobNumber + "]+  " + statusPadded + job.command + " &");
                }
            } else if (cmd.equals("type")) {
                if (parts.size() < 2) {
                    err.println("type: missing operand");
                } else {
                    String commandName = parts.get(1);
                    if (builtins.contains(commandName)) {
                        out.println(commandName + " is a shell builtin");
                    } else {
                        File executable = findExecutable(commandName);
                        if (executable != null) {
                            out.println(commandName + " is " + executable.getAbsolutePath());
                        } else {
                            err.println(commandName + ": not found");
                        }
                    }
                }
            }

            else {
                File executable = findExecutable(cmd);
                if (executable == null) {
                    err.println(cmd + ": command not found");
                } else {
                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.directory(currentDir);

                    if (outputFile != null) {
                        pb.redirectOutput(appendOut ? ProcessBuilder.Redirect.appendTo(new File(outputFile))
                                : ProcessBuilder.Redirect.to(new File(outputFile)));
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (errorFile != null) {
                        pb.redirectError(appendErr ? ProcessBuilder.Redirect.appendTo(new File(errorFile))
                                : ProcessBuilder.Redirect.to(new File(errorFile)));
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    Process process = pb.start();

                    if (isBackground) {
                        // Track the background job
                        jobCounter++;
                        long pid = process.pid();
                        jobs.add(new Job(jobCounter, pid, commandStr, "Running"));
                        System.out.println("[" + jobCounter + "] " + pid);
                    } else {
                        process.waitFor();
                    }
                }
            }

            if (out != System.out)
                out.close();
            if (err != System.err)
                err.close();
        }

        sc.close();
    }
}