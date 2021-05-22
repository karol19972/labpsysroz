import java.io.IOException;

public class Server {
    public static void main(String args[]) throws IOException {
        Process process = Runtime.getRuntime().exec("cmd.exe /c start java -Xmx256m -Xms256m -jar server/lib/kvstore.jar kvlite -secure-config disable");
    }
}
