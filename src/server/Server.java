
package server;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpExchange;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


public class Server {
    static FileHandler handler;
    static Logger logger;
    static int Port = 6175;
    
    public static void main(String[] args) {
        
        try {
            //setup logger
            handler = new FileHandler("server.log", false);
            handler.setFormatter(new SimpleFormatter());
            logger = Logger.getLogger(Server.class.getName());
            logger.addHandler(handler);
            
            //Identify port change -- default 6175
            if (args.length == 1)
                Port = Integer.parseInt(args[0]);
            else if(args.length > 1){
                System.out.println("Usage:\nserver <port_number>");
                logger.severe("incorrect arguments");
                logger.exiting(Server.class.getName(), "main");
                System.exit(1);
            }
            //setup server with maximum of 10 threads
            logger.info("listening on port " + Port);
            HttpServer server = HttpServer.create(new InetSocketAddress(Port), 0);
            server.createContext("/", new MyHandler());
            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();
        }
        catch (IOException | SecurityException e) {
            logger.log(Level.SEVERE, "Error", e);
        }
    }
    
    static class MyHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange t) throws IOException {
            logger.info(Thread.currentThread().getId() + " -- Request Method: " + t.getRequestMethod());
            //Post method
            if (t.getRequestMethod().equals("POST"))
            {
                //input
                InputStreamReader isr = new InputStreamReader(t.getRequestBody(), "utf-8");
                BufferedReader in = new BufferedReader(isr);
                String value = in.readLine();                   //read xml
                logger.info(Thread.currentThread().getId() + " -- Recieved: " + value);
                String output = this.performTask(value);  //perform task and generate response xml
                in.close();
                isr.close();
                //output
                t.sendResponseHeaders(200, output.length());
                OutputStream out = t.getResponseBody();
                out.write(output.getBytes());
                out.close();
                logger.info(Thread.currentThread().getId() + " -- Response sent");
                return;
            }
        }
        
        public String performTask(String input){
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder;
            String output = null;
            
            try {
                dBuilder = dbFactory.newDocumentBuilder();
                Document xml = dBuilder.parse(new InputSource(new ByteArrayInputStream(input.getBytes("utf-8"))));
                xml.getDocumentElement().normalize();
                
                logger.info(Thread.currentThread().getId() + " -- Request Type: " + xml.getDocumentElement().getNodeName());
                String command = xml.getDocumentElement().getAttribute("command");
                logger.info(Thread.currentThread().getId() + " -- Command: " + command);
                String result = null;
                Future<String> future = null;
                
                if (command.equalsIgnoreCase("print") || command.equalsIgnoreCase("delete") || command.equalsIgnoreCase("save")) //insert more commands as needed
                {
                    //Create thread for peforming tasks based on command
                    ExecutorService es = Executors.newSingleThreadExecutor();
                    Callable<String> task = () -> {
                        try {
                            //perform all the task need here  ---> just gonna sleep for 2 seconds;
                            Thread.sleep(2000);
                        } catch (InterruptedException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                        
                        return "Successful";
                    };
                
                    future = es.submit(task);
                    
                }else {
                    result = "bad command";
                }
                ///Continue to perform other items if needed
                //Create XML response
                if (result == null) result = future.get();
                
                Node ticket = xml.getChildNodes().item(0);
                //this could be created using DOM
                output = "<Response status=\"" + result + 
                        "\"><ticketid>" + ticket.getTextContent() + 
                        "</ticketid><datetime>" + (new Timestamp(System.currentTimeMillis())) +
                        "</datetime></Response>";
                
                logger.info(Thread.currentThread().getId() + " -- Generated xml: " + output);
            } catch (ParserConfigurationException | SAXException | IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            return output;
        }
        
    }
    
}
