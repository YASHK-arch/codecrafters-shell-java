import java.util.*;
import java.io.*;

public class Main {

    interface PipelineTask {
        void start(InputStream in, OutputStream out, PrintStream err, boolean closeOut, File[] currentDirWrap, Set<String> builtins, List<Job> jobs) throws Exception;
        void waitFor() throws Exception;
        Long getPid();
        boolean isAlive();
    }

    static class ProcessTask implements PipelineTask {
        List<String> command;
        Process process;
        Thread outThread;
        Thread errThread;
        Thread inThread;

        ProcessTask(List<String> command) {
            this.command = command;
        }

        public void start(InputStream in, OutputStream out, PrintStream err, boolean closeOut, File[] currentDirWrap, Set<String> builtins, List<Job> jobs) throws Exception {
            File executable = findExecutable(command.get(0));
            if (executable == null) {
                err.println(command.get(0) + ": command not found");
                if (closeOut && out != null) {
                    try { out.close(); } catch(Exception e) {}
                }
                if (in != null) {
                    try { in.close(); } catch(Exception e) {}
                }
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(currentDirWrap[0]);

            this.process = pb.start();

            outThread = new Thread(() -> {
                try {
                    copyStream(process.getInputStream(), out);
                } catch (IOException e) {}
                finally {
                    try { process.getInputStream().close(); } catch(Exception e) {}
                    if (closeOut) {
                        try { out.close(); } catch(Exception e) {}
                    }
                }
            });
            outThread.start();

            errThread = new Thread(() -> {
                try {
                    copyStream(process.getErrorStream(), err);
                } catch (IOException e) {}
                finally {
                    try { process.getErrorStream().close(); } catch(Exception e) {}
                }
            });
            errThread.start();

            if (in != null) {
                inThread = new Thread(() -> {
                    try {
                        copyStream(in, process.getOutputStream());
                    } catch (IOException e) {}
                    finally {
                        try { process.getOutputStream().close(); } catch(Exception e) {}
                        try { in.close(); } catch(Exception e) {}
                    }
                });
                inThread.start();
            } else {
                process.getOutputStream().close();
            }
        }

        public void waitFor() throws Exception {
            if (process != null) {
                process.waitFor();
            }
            if (outThread != null) outThread.join();
            if (errThread != null) errThread.join();
            if (inThread != null) inThread.join();
        }

        public Long getPid() {
            return process != null ? process.pid() : null;
        }

        public boolean isAlive() {
            return process != null && process.isAlive();
        }
    }

    static class BuiltinTask implements PipelineTask {
        List<String> command;
        Thread thread;

        BuiltinTask(List<String> command) {
            this.command = command;
        }

        public void start(InputStream in, OutputStream out, PrintStream err, boolean closeOut, File[] currentDirWrap, Set<String> builtins, List<Job> jobs) throws Exception {
            thread = new Thread(() -> {
                try {
                    PrintStream outPrint = new PrintStream(out, true);
                    String cmd = command.get(0);

                    if (cmd.equals("exit")) {
                        System.exit(0);
                    } else if (cmd.equals("echo")) {
                        for (int i = 1; i < command.size(); i++) {
                            if (i > 1) outPrint.print(" ");
                            outPrint.print(command.get(i));
                        }
                        outPrint.println();
                    } else if (cmd.equals("pwd")) {
                        outPrint.println(currentDirWrap[0].getAbsolutePath());
                    } else if (cmd.equals("cd")) {
                        if (command.size() < 2) {
                            err.println("cd: missing operand");
                        } else {
                            String dirPath = command.get(1);
                            File newDir;
                            if (dirPath.equals("~")) {
                                newDir = new File(System.getenv("HOME"));
                            } else if (dirPath.startsWith("/")) {
                                newDir = new File(dirPath);
                            } else {
                                newDir = new File(currentDirWrap[0], dirPath);
                            }

                            if (newDir.exists() && newDir.isDirectory()) {
                                currentDirWrap[0] = newDir.getCanonicalFile();
                            } else {
                                err.println("cd: " + dirPath + ": No such file or directory");
                            }
                        }
                    } else if (cmd.equals("jobs")) {
                        for (Job job : jobs) {
                            if (job.task != null && !job.task.isAlive()) {
                                job.status = "Done";
                            }
                        }
                        for (int i = 0; i < jobs.size(); i++) {
                            Job job = jobs.get(i);
                            char marker = ' ';
                            if (i == jobs.size() - 1) marker = '+';
                            else if (i == jobs.size() - 2) marker = '-';

                            String statusPadded = String.format("%-24s", job.status);
                            String commandLine = job.command;
                            if (job.status.equals("Running")) {
                                commandLine += " &";
                            }
                            outPrint.println("[" + job.jobNumber + "]" + marker + "  " + statusPadded + commandLine);
                        }
                        jobs.removeIf(job -> job.status.equals("Done"));
                    } else if (cmd.equals("type")) {
                        if (command.size() < 2) {
                            err.println("type: missing operand");
                        } else {
                            String commandName = command.get(1);
                            if (builtins.contains(commandName)) {
                                outPrint.println(commandName + " is a shell builtin");
                            } else {
                                File executable = findExecutable(commandName);
                                if (executable != null) {
                                    outPrint.println(commandName + " is " + executable.getAbsolutePath());
                                } else {
                                    err.println(commandName + ": not found");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace(err);
                } finally {
                    if (closeOut) {
                        try { out.close(); } catch(Exception e) {}
                    }
                    if (in != null) {
                        try { in.close(); } catch(Exception e) {}
                    }
                }
            });
            thread.start();
        }

        public void waitFor() throws Exception {
            if (thread != null) {
                thread.join();
            }
        }

        public Long getPid() {
            return 0L;
        }

        public boolean isAlive() {
            return thread != null && thread.isAlive();
        }
    }

    static class Job {
        int jobNumber;
        long pid;
        String command;
        String status;
        PipelineTask task;

        Job(int jobNumber, long pid, String command, String status, PipelineTask task) {
            this.jobNumber = jobNumber;
            this.pid = pid;
            this.command = command;
            this.status = status;
            this.task = task;
        }
    }

    static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
            out.flush();
        }
    }

    private static List<String> parseCommand(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

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
            if (ch == '\\' && !inSingleQuotes && !inDoubleQuotes) {
                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
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
                    if (i > 0 && input.charAt(i - 1) == '>') {
                        continue;
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

    private static void reapJobs(List<Job> jobs, PrintStream out) {
        for (Job job : jobs) {
            if (job.task != null && !job.task.isAlive()) {
                job.status = "Done";
            }
        }

        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            if (job.status.equals("Done")) {
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

        File[] currentDirWrap = new File[] { new File(System.getProperty("user.dir")) };
        List<Job> jobs = new ArrayList<>();

        while (true) {
            reapJobs(jobs, System.out);

            System.out.print("$ ");
            if (!sc.hasNextLine()) break;
            String command = sc.nextLine();

            List<String> parts = parseCommand(command);

            boolean isBackground = false;
            if (!parts.isEmpty() && parts.get(parts.size() - 1).equals("&")) {
                isBackground = true;
                parts.remove(parts.size() - 1);
            }

            String commandStr = String.join(" ", parts);

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

            if (redirectIndex != -1) {
                parts = new ArrayList<>(parts.subList(0, redirectIndex));
            }

            if (parts.isEmpty()) {
                if (out != System.out) out.close();
                if (err != System.err) err.close();
                continue;
            }

            List<List<String>> pipelineCommands = new ArrayList<>();
            List<String> currentCmd = new ArrayList<>();
            for (String part : parts) {
                if (part.equals("|")) {
                    if (!currentCmd.isEmpty()) {
                        pipelineCommands.add(currentCmd);
                        currentCmd = new ArrayList<>();
                    }
                } else {
                    currentCmd.add(part);
                }
            }
            if (!currentCmd.isEmpty()) {
                pipelineCommands.add(currentCmd);
            }

            if (pipelineCommands.isEmpty()) {
                if (out != System.out) out.close();
                if (err != System.err) err.close();
                continue;
            }

            List<PipelineTask> tasks = new ArrayList<>();
            for (List<String> pCmd : pipelineCommands) {
                if (builtins.contains(pCmd.get(0))) {
                    tasks.add(new BuiltinTask(pCmd));
                } else {
                    tasks.add(new ProcessTask(pCmd));
                }
            }

            InputStream currentIn = null;

            for (int i = 0; i < tasks.size(); i++) {
                PipelineTask task = tasks.get(i);
                OutputStream taskOut;
                InputStream nextIn = null;
                boolean closeOut = false;

                if (i == tasks.size() - 1) {
                    taskOut = out;
                } else {
                    PipedOutputStream pos = new PipedOutputStream();
                    PipedInputStream pis = new PipedInputStream(pos);
                    taskOut = pos;
                    nextIn = pis;
                    closeOut = true;
                }

                task.start(currentIn, taskOut, err, closeOut, currentDirWrap, builtins, jobs);
                currentIn = nextIn;
            }

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
                PipelineTask lastTask = tasks.get(tasks.size() - 1);
                Long pid = lastTask.getPid();
                if (pid == null) pid = 0L;
                
                jobs.add(new Job(nextJobNumber, pid, commandStr, "Running", lastTask));
                System.out.println("[" + nextJobNumber + "] " + pid);
            } else {
                for (PipelineTask task : tasks) {
                    task.waitFor();
                }
            }

            if (out != System.out) out.close();
            if (err != System.err) err.close();
        }

        sc.close();
    }
}