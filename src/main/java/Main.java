import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String command = sc.nextLine();
            if (command.equals("exit")){
                break;
            }
            else if (command.startsWith("echo ")) {
                String message = command.substring(5);
                System.out.println(message);
            }
            // else if (command.equals("help")) {
            //     System.out.println("Available commands:");
            //     System.out.println("echo [message] - prints the message");
            //     System.out.println("help - displays this help message");
            //     System.out.println("exit - exits the program");
            // }
            else {
            System.out.println(command + ": command not found");
        }

    }
}
