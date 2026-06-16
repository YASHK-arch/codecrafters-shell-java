import java.util.Scanner;
import java.util.*;

import java.io.File;
import java.io.*;

public class Main {
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
                String message = command.substring(5);
                System.out.println(message);

            } else if (command.startsWith("type ")) {
                String message = command.substring(5).trim();

                // Step 1: check builtins
                if (builtin.contains(message)) {
                    System.out.println(message + " is a shell builtin");
                    continue;
                }

                // Step 2: check PATH
                String pathEnv = System.getenv("PATH");
                if (pathEnv != null) {
                    String[] pathDirs = pathEnv.split(File.pathSeparator);
                    boolean found = false;
                    for (String dir : pathDirs) {
                        File file = new File(dir, message);
                        if (file.exists() && file.canExecute()) {
                            System.out.println(message + " is " + file.getAbsolutePath());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        System.out.println(message + ": not found");
                    }
                } else {
                    System.out.println(message + ": not found");
                }

            } else {
                System.out.println(command + ": command not found");
            }
        }

        sc.close();
    }
}
