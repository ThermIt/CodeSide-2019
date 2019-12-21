import model.UnitAction;
import older.MyOlderStrategy;
import util.Debug;
import util.Strategy;
import util.StreamUtil;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class Runner {
    private final InputStream inputStream;
    private final OutputStream outputStream;

    Runner(String host, int port, String token) throws IOException {
        Socket socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        inputStream = new BufferedInputStream(socket.getInputStream());
        outputStream = new BufferedOutputStream(socket.getOutputStream());
        StreamUtil.writeString(outputStream, token);
        outputStream.flush();
    }

    private static void runOnce(String host, int port, String token, boolean older) {
        Runnable task = () -> {
            try {
                new Runner(host, port, token).run(older);
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    public static void main(String[] args) throws IOException {
        String host = args.length < 1 ? "127.0.0.1" : args[0];
        int port = args.length < 2 ? 31001 : Integer.parseInt(args[1]);
        String token = args.length < 3 ? "0000000000000000" : args[2];
        if (args.length > 3 && "double".equals(args[3])) {
            runOnce(host, port, token, false);
            runOnce(host, port + 1, token, true);
        } else  {
            new Runner(host, port, token).run();
        }
    }

    void run() throws IOException {
        run(false);
    }

    void run(boolean older) throws IOException {
        try {
            Strategy myStrategy = older ? new MyOlderStrategy() : new MyStrategy();
            Debug debug = new Debug(outputStream);
            while (true) {
                model.ServerMessageGame message = model.ServerMessageGame.readFrom(inputStream);
                model.PlayerView playerView = message.getPlayerView();
                if (playerView == null) {
                    break;
                }
                Map<Integer, UnitAction> actions = myStrategy.getAllActions(playerView, debug);
                new model.PlayerMessageGame.ActionMessage(new model.Versioned(actions)).writeTo(outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            System.out.println(e.toString());
            e.printStackTrace();
            throw e;
        }
    }
}