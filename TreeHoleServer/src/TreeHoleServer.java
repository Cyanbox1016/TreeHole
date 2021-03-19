/**
 * @author Wang Yongqi 3180105481
 * Server program of the Treehole BBS system
 */
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TreeHoleServer {
    // port and socket element of the listing socket
    public static final int listeningPort = 5481;
    public static ServerSocket serverSocket;
    
    public static void main(String[] args) throws Exception {
        try {
            serverSocket = new ServerSocket(listeningPort);
        } catch (IOException e) {
            System.out.println("ERROR: Socket failed.");
            System.exit(-1);
        }
        
        // create a thread pool to distribute threads for clients
        ExecutorService threadPool = Executors.newFixedThreadPool(100);

        while (true) {
            Socket socket = serverSocket.accept();
            
            // thread runnable to communicate with clients
            Runnable runnable = ()-> {
                InputStream inputStream = null;
                OutputStream outputStream = null;
                try {
                    inputStream = socket.getInputStream();
                    outputStream = socket.getOutputStream();
                } catch (IOException e) {
                    System.err.println("ERROR: socket error detected, the server is turned off.");
                    System.exit(1);
                }

                BufferedReader bufferedReader = null;
                try {
                    bufferedReader = new BufferedReader(new InputStreamReader(inputStream,"UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    System.err.println("ERROR: unsupported Encoding");
                }

                String readline;
                try {
                    while ((readline = bufferedReader.readLine()) != null) {
                        // return due to different message types
                        switch (readline) {
                            // send the content requested by the client
                            case "GET" -> {
                                String filepath = bufferedReader.readLine();
                                FileInputStream fileInputStream = new FileInputStream(filepath);
                                BufferedReader bufferedReader1 = new BufferedReader(new InputStreamReader(fileInputStream, "UTF-8"));
                                outputStream.write("$$START%%\n".getBytes());
                                String fileLine;
                                while ((fileLine = bufferedReader1.readLine()) != null) {
                                    fileLine = fileLine + "\n";
                                    outputStream.write(fileLine.getBytes(StandardCharsets.UTF_8));
                                }
                                outputStream.write("%%END$$\n".getBytes());
                                fileInputStream.close();
                            }
                            // write the content from the client to the file
                            case "POST" -> {
                                String type = bufferedReader.readLine();
                                switch (type) {
                                    case "NEWPOST" -> {
                                        String id = bufferedReader.readLine();
                                        File postFile = new File("res\\" + id + ".txt");
                                        postFile.createNewFile();
                                        FileOutputStream postFileOutputStream = new FileOutputStream(postFile);

                                        while ((readline = bufferedReader.readLine()) != null) {
                                            if (readline.equals("%%END$$")) {
                                                break;
                                            } else {
                                                postFileOutputStream.write(readline.getBytes(StandardCharsets.UTF_8));
                                                postFileOutputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                                            }
                                        }
                                        postFileOutputStream.close();
                                    }
                                    case "COMMENT" -> {
                                        String id = bufferedReader.readLine();
                                        File postFile = new File("res\\" + id + ".txt");
                                        FileOutputStream postFileOutputStream = new FileOutputStream(postFile, true);

                                        while ((readline = bufferedReader.readLine()) != null) {
                                            if (readline.equals("%%END$$")) {
                                                break;
                                            } else {
                                                postFileOutputStream.write(readline.getBytes(StandardCharsets.UTF_8));
                                                postFileOutputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                                            }
                                        }
                                        postFileOutputStream.close();
                                    }
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("WARNING: file I/O excption detected.");
                    System.err.println("Please check the path of the files.");
                }
            };
            // submit the request to the thread pool
            threadPool.submit(runnable);
        }
    }
}
