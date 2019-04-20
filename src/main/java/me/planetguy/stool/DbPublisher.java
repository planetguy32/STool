package me.planetguy.stool;

import java.io.*;
import java.net.*;
import com.sun.net.httpserver.*;
import java.io.File;
import java.nio.file.Files;
import java.util.Random;

public class DbPublisher implements HttpHandler {

    private final File backupDb;
    private final PrintWriter log;

    public DbPublisher() {
        File db = new File("stool.db");
        backupDb = new File("stool.db.bak");
        PrintWriter log_;
        try {
            log_ = new PrintWriter(new FileWriter("stool-get.log"));
            if(backupDb.exists())
                backupDb.delete();
            Files.copy(db.toPath(), backupDb.toPath());
            HttpServer server = HttpServer.create(new InetSocketAddress(1812),0);
            server.createContext("/", this);
            server.start();
        } catch(IOException e) {
            e.printStackTrace();
            log_ = null;
            throw new RuntimeException(e);
        }
        log = log_;
    }

    public int generateNonsenseHttpCode() {
        int[] httpCodes = new int[]{
            400, 401, 402, 403, 404, 418, 429, 431, 451, 500, 501, 502, 503, 504, 505, 511, 520
        };
        return httpCodes[new Random().nextInt(httpCodes.length)];
    }

    public void handle(HttpExchange t) throws IOException {
        URI uri = t.getRequestURI();
        log.println("HTTP request "+t.getRemoteAddress()+": "+uri);
        String secret = "/uncrown-occupant-snowbound-monday-dealing/stool.db";
        if (!uri.getPath().equals(secret)) {
            // Not actually the URL for the DB
            String response = "Access denied, ask planetguy for the URL\n";
            t.sendResponseHeaders(generateNonsenseHttpCode(), response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        } else {
            t.sendResponseHeaders(200, 0);
            OutputStream os = t.getResponseBody();
            FileInputStream fs = new FileInputStream(backupDb);
            final byte[] buffer = new byte[0x10000];
            int count = 0;
            while ((count = fs.read(buffer)) >= 0) {
                os.write(buffer,0,count);
            }
            fs.close();
            os.close();
        }
    }

}
