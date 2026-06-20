import java.util.*;
import java.io.*;

public class Main {

    // Job class to store background job information
    static class Job {
        int jobNumber;
        long pid;
        String command;
        String status;
        Process process;

        Job(int jobNumber, long pid, String command, String status, Process process) {
            this.jobNumber = jobNumber;
            this.pid = pid;
            this.command = command;
            this.status = status;
            this.process = process;
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
                if (ch == '|') {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    tokens.add("|");
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
        String[] pathDirs = pathEnv.split(File.pathSeparator);
        for (String dir : pathDirs) {
            File file = new File(dir, commandName);
            if (file.exists() && file.canExecute()) {
                return file;
            }
        }
        return null;
    }

    // Reap completed background jobs and display them as Done
    private static void reapJobs(List<Job> jobs, PrintStream out) {
        // Check which jobs have completed
        for (Job job : jobs) {
            if (!job.process.isAlive()) {
                job.status = "Done";
            }
        }

        // Display completed jobs as Done
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            if (job.status.equals("Done")) {
                // Determine marker: + for most recent, - for second most recent, space for
                // others
                char marker = ' ';
                if (i == jobs.size() - 1) {
                    marker = '+';
                } else if (i == jobs.size() - 2) {
                    marker = '-';
                }
                String statusPadded = String.format("%-24s", job.status);
                out.println("[" + job.jobNumber + "]" + marker + "  " + statusPadded + job.command);
            }
        }

        // Remove completed jobs
        jobs.removeIf(job -> job.status.equals("Done"));
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
        List<Job> jobs = new ArrayList<>();

        while (true) {
            // Reap completed background jobs before prompt
            reapJobs(jobs, System.out);

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

            if (parts.isEmpty()) {
                if (out != System.out) out.close();
                if (err != System.err) err.close();
                continue;
            }

            int pipeIndex = parts.indexOf("|");

            if (pipeIndex != -1) {
                List<String> leftParts = new ArrayList<>(parts.subList(0, pipeIndex));
                List<String> rightParts = new ArrayList<>(parts.subList(pipeIndex + 1, parts.size()));

                File executableLeft = findExecutable(leftParts.get(0));
                File executableRight = findExecutable(rightParts.get(0));

                if (executableLeft == null) {
                    err.println(leftParts.get(0) + ": command not found");
                } else if (executableRight == null) {
                    err.println(rightParts.get(0) + ": command not found");
                } else {
                    ProcessBuilder pb1 = new ProcessBuilder(leftParts);
                    pb1.directory(currentDir);
                    pb1.redirectError(ProcessBuilder.Redirect.INHERIT);

                    ProcessBuilder pb2 = new ProcessBuilder(rightParts);
                    pb2.directory(currentDir);

                    if (outputFile != null) {
                        pb2.redirectOutput(appendOut ? ProcessBuilder.Redirect.appendTo(new File(outputFile))
                                : ProcessBuilder.Redirect.to(new File(outputFile)));
                    } else {
                        pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (errorFile != null) {
                        pb2.redirectError(appendErr ? ProcessBuilder.Redirect.appendTo(new File(errorFile))
                                : ProcessBuilder.Redirect.to(new File(errorFile)));
                    } else {
                        pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    List<ProcessBuilder> builders = Arrays.asList(pb1, pb2);
                    List<Process> processes = ProcessBuilder.startPipeline(builders);

                    Process lastProcess = processes.get(processes.size() - 1);

                    if (isBackground) {
                        int nextJobNumber = 1;
                        if (!jobs.isEmpty()) {
                            int maxJobNumber = 0;
                            for (Job j : jobs) {
                                if (j.jobNumber > maxJobNumber) {
                                    maxJobNumber = j.jobNumber;
                                }
                            }
                            nextJobNumber = maxJobNumber + 1;
                        }
                        long pid = lastProcess.pid();
                        jobs.add(new Job(nextJobNumber, pid, commandStr, "Running", lastProcess));
                        System.out.println("[" + nextJobNumber + "] " + pid);
                    } else {
                        lastProcess.waitFor();
                    }
                }

                if (out != System.out) out.close();
                if (err != System.err) err.close();
                continue;
            }

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
                // First, check which jobs have completed
                for (Job job : jobs) {
                    if (!job.process.isAlive()) {
                        job.status = "Done";
                    }
                }

                // Display all jobs
                for (int i = 0; i < jobs.size(); i++) {
                    Job job = jobs.get(i);
                    // Determine marker: + for most recent, - for second most recent, space for
                    // others
                    char marker = ' ';
                    if (i == jobs.size() - 1) {
                        // Most recent job (last in list)
                        marker = '+';
                    } else if (i == jobs.size() - 2) {
                        // Second most recent job
                        marker = '-';
                    }
                    // Format: [1]+ Running sleep 10 &
                    String statusPadded = String.format("%-24s", job.status);
                    String commandLine = job.command;
                    if (job.status.equals("Running")) {
                        commandLine += " &";
                    }
                    out.println("[" + job.jobNumber + "]" + marker + "  " + statusPadded + commandLine);
                }

                // Remove completed jobs
                jobs.removeIf(job -> job.status.equals("Done"));
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
                        int nextJobNumber = 1;
                        if (!jobs.isEmpty()) {
                            int maxJobNumber = 0;
                            for (Job j : jobs) {
                                if (j.jobNumber > maxJobNumber) {
                                    maxJobNumber = j.jobNumber;
                                }
                            }
                            nextJobNumber = maxJobNumber + 1;
                        }
                        long pid = process.pid();
                        jobs.add(new Job(nextJobNumber, pid, commandStr, "Running", process));
                        System.out.println("[" + nextJobNumber + "] " + pid);
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