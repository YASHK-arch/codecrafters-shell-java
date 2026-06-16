import java.util.Scanner;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String command = sc.nextLine();
            if (command.equals("exit")) {
                break;
            } else if (command.startsWith("echo ")) {
                String message = command.substring(5);
                System.out.println(message);
            } else if (command.startsWith("type")) {
                HashSet<String> builtin = new HashSet<>();
                builtin.add("echo");
                builtin.add("type");
                builtin.add("exit");
                String message = command.substring(5);
                if (builtin.contains(message)) {
                    System.out.println(message + " is a shell builtin");
                } else {
                    System.out.println(message + ": not found");
                }

            } else {
                System.out.println(command + ": command not found");
            }

        }
    }
}
