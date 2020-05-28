import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class PrintHelloFromJava {
    public static void main(String[] args) throws FileNotFoundException {
        try (PrintWriter obj = new PrintWriter("log.txt")) {
            obj.println("Hello from Java!");
        }

        System.exit(0);
    }
}