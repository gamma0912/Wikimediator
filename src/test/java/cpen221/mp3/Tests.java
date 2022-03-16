package cpen221.mp3;
import com.google.gson.JsonObject;
import cpen221.mp3.fsftbuffer.*;
import cpen221.mp3.server.WikiMediatorClient;
import cpen221.mp3.server.WikiMediatorServer;
import cpen221.mp3.wikimediator.WikiMediator;
import org.fastily.jwiki.core.Wiki;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;

public class Tests {

    private static WikiMediatorServer server;

    @Test
    public void testBufferInstantiation() {
        FSFTBuffer<Website> binst = new FSFTBuffer<>(); //test no arg constructor
        Website websitebufinst1 = new Website("websitebufinst1", "Obama");
        Assertions.assertTrue(binst.put(websitebufinst1));
        Assertions.assertEquals(websitebufinst1, binst.get(websitebufinst1.id()));
    }

    @Test
    public void testBufferPut() {
        FSFTBuffer<Website> bput = new FSFTBuffer<>(2, 1);
        Website websitebufput1 = new Website("websitebufput1", "1");
        Website websitebufput2 = new Website("websitebufput2", "2");
        Assertions.assertTrue(bput.put(websitebufput1));
        Assertions.assertTrue(bput.put(websitebufput2));
        Website putResult1;
        Website putResult2;
        try {
            putResult1 = bput.get(websitebufput1.id());
        } catch (NoSuchElementException e) {
            fail("Unexpected exception!");
        }
        try {
            putResult2 = bput.get(websitebufput2.id());
        } catch (NoSuchElementException e) {
            fail("Unexpected exception!");
        }
        Website websitebufput1fake = new Website("websitebufput1", "Lincoln");//test put existing id
        Assertions.assertFalse(bput.put(websitebufput1fake));
        Assertions.assertEquals(websitebufput1.getContent(), bput.get(websitebufput1fake.id()).getContent());
        try {
            Thread.sleep(1010);
        } catch (InterruptedException e) {}
        Assertions.assertTrue(bput.put(websitebufput1fake)); //test putting expired website
        Assertions.assertEquals(websitebufput1fake.getContent(), bput.get(websitebufput1fake.id()).getContent());
    }

    @Test
    public void testBufferGet() {
        FSFTBuffer<Website> bget = new FSFTBuffer<>(100, 1);
        Website websitebufget1 = new Website("websitebufget1", "1");
        Website websitebufget2 = new Website("websitebufget2", "2");
        Website websitebufget1e = new Website("websitebufget1", "11");
        Website getResult1;
        boolean exceptionThrownGet1 = false;
        try {
            getResult1 = bget.get("1");
        } catch (NoSuchElementException e) {
            exceptionThrownGet1 = true;
        } finally {
            if (!exceptionThrownGet1) {
                fail("Expected NoSuchElementException!");
            }
        }
        bget.put(websitebufget1);
        Website getResult2 = null;
        try {
            getResult2 = bget.get("websitebufget1");
        } catch (NoSuchElementException e) {
            fail("Unexpected exception!");
        }
        Assertions.assertEquals(websitebufget1, getResult2);
        try {
            Thread.sleep(1010);
        } catch (InterruptedException e) {
            //do nothing
        }
        Website getResult3; //test getting expired object
        boolean exceptionThrownGet3 = false;
        try {
            getResult3 = bget.get("websitebufget1");
        } catch (NoSuchElementException e) {
            exceptionThrownGet3 = true;
        } finally {
            if (!exceptionThrownGet1) {
                fail("Expected NoSuchElementException!");
            }
        }
        Assertions.assertFalse(bget.touch("websitebufget1"));
        Assertions.assertFalse(bget.update(websitebufget1e));
    }

    @Test
    public void testTouch() {
        FSFTBuffer<Website> btouch = new FSFTBuffer<>(100, 1);
        Website websitebuftouch1 = new Website("websitebuftouch1", "1");
        btouch.put(websitebuftouch1);
        Website touchResult1 = null;
        try {
            touchResult1 = btouch.get("websitebuftouch1");
        } catch (NoSuchElementException e) {
            fail("Unexpected exception!");
        }
        Assertions.assertEquals(websitebuftouch1, touchResult1);
        try {
            Thread.sleep(550);
        } catch (InterruptedException e) {
            // do nothing
        }
        Assertions.assertTrue(btouch.touch("websitebuftouch1"));
        Assertions.assertFalse(btouch.touch("blah"));
        try {
            Thread.sleep(550);
        } catch (InterruptedException e) {
            // do nothing
        }
        Website touchResult2 = null;
        try {
            touchResult2 = btouch.get("websitebuftouch1");
        } catch (NoSuchElementException e) {
            fail("Unexpected exception!");
        }
        Assertions.assertEquals(websitebuftouch1, touchResult2);
        try {
            Thread.sleep(1010);
        } catch (InterruptedException e) {
            // do nothing
        }
        Website touchResult3; //test touching expired object
        boolean exceptionThrownTouch3 = false;
        try {
            touchResult3 = btouch.get("websitebufget1");
        } catch (NoSuchElementException e) {
            exceptionThrownTouch3 = true;
        } finally {
            if (!exceptionThrownTouch3) {
                fail("Expected NoSuchElementException!");
            }
        }
    }

    @Test
    public void testUpdate() {
        FSFTBuffer<Website> bupdate = new FSFTBuffer<>(100, 100);
        Website websitebufupdate1 = new Website("websitebufupdate1", "1");
        Website websitebufupdate1new = new Website("websitebufupdate1", "2");
        bupdate.put(websitebufupdate1);
        Assertions.assertEquals(websitebufupdate1.getContent(),
                bupdate.get("websitebufupdate1").getContent());
        Assertions.assertTrue(bupdate.update(websitebufupdate1new));
        Assertions.assertEquals(websitebufupdate1new.getContent(),
                bupdate.get("websitebufupdate1").getContent());
        Website websitebufupdategarbage= new Website("garbage", "2");
        Assertions.assertFalse(bupdate.update(websitebufupdategarbage));
    }

    @Test
    public void testRemoveLeastRequestedUsed() {
        FSFTBuffer<Website> bLRU = new FSFTBuffer<>(2, 100);
        Website websitebufLRU1 = new Website("websitebufLRU1", "1");
        Website websitebufLRU2 = new Website("websitebufLRU2", "2");
        Website websitebufLRU3 = new Website("websitebufLRU3", "3");
        bLRU.put(websitebufLRU1);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            // do nothing
        }
        bLRU.put(websitebufLRU2);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            // do nothing
        }
        Website temp = bLRU.get("websitebufLRU1");
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            // do nothing
        }
        bLRU.put(websitebufLRU3);
        Website LRUResult1 = null;
        Website LRUResult2 = null;
        Website LRUResult3 = null;
        try {
            LRUResult1 = bLRU.get(websitebufLRU1.id());
        } catch (NoSuchElementException e) {
            fail("Unexpected exception!");
        }
        try {
            LRUResult3 = bLRU.get(websitebufLRU3.id());
        } catch (NoSuchElementException e) {
            fail("Unexpected exception!");
        }
        boolean LRUExceptionThrown2 = false;
        try {
            LRUResult2 = bLRU.get(websitebufLRU2.id());
        } catch (NoSuchElementException e) {
            LRUExceptionThrown2 = true;
        } finally {
            if (!LRUExceptionThrown2) {
                fail("Expected NoSuchElementException!");
            }
        }
        Assertions.assertEquals(websitebufLRU1, LRUResult1);
        Assertions.assertEquals(websitebufLRU3, LRUResult3);
    }

    @Test
    public void testManyThreadsAll() {
        FSFTBuffer<Website> bthread1 = new FSFTBuffer<>(2, 3);
        Website websitebufthread1 = new Website("websitebufthread1", "1");
        Website websitebufthread2 = new Website("websitebufthread2", "2");
        Website websitebufthread2new = new Website("websitebufthread2", "222");
        Website websitebufthread3 = new Website("websitebufthread3", "3");
        Thread bclient1 = new Thread(new Runnable() {
            public void run() {
                try {
                    bthread1.put(websitebufthread1);
                    Thread.sleep(2);
                    bthread1.put(websitebufthread3);
                    Thread.sleep(2);
                    bthread1.get("websitebufthread1");
                } catch (InterruptedException ioe) {
                    //do nothing
                } catch (NoSuchElementException e) {
                    fail("Unexpected exception!");
                }
            }
        });
        Thread bclient2 = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(7);
                    bthread1.put(websitebufthread2);
                } catch (InterruptedException ioe) {
                    // do nothing
                } catch (NoSuchElementException e) {
                    fail("Unexpected exception!");
                }
            }
        });

        try {
            bclient1.start();
            bclient2.start();
            Thread.sleep(1010);
            Assertions.assertEquals(bthread1.get("websitebufthread2"), websitebufthread2);
            Assertions.assertEquals(bthread1.get("websitebufthread1"), websitebufthread1);
            Website threadResult1 = null;
            boolean threadExceptionThrown1 = false;
            try {
                threadResult1 = bthread1.get(websitebufthread3.id());
            } catch (NoSuchElementException e) {
                threadExceptionThrown1 = true;
            } finally {
                if (!threadExceptionThrown1) {
                    fail("Expected NoSuchElementException!");
                }
            }
            Thread.sleep(1010);
            bthread1.touch("websitebufthread1");
            bthread1.update(websitebufthread2new);
            Thread.sleep(2020);
            Assertions.assertEquals(bthread1.get("websitebufthread1"),
                    websitebufthread1);
            Assertions.assertEquals(bthread1.get("websitebufthread2").getContent(),
                    websitebufthread2new.getContent());
        } catch (InterruptedException ioe) {
            // do nothing
        } catch (NoSuchElementException e) {
            fail("Unexpected exception!");
        }
    }

    @Test
    public void testWikiMediatorConstructor() {
        WikiMediator wm = new WikiMediator(5, 100);
        Assertions.assertFalse(wm.getPage("Barack Obama").isEmpty());
    }

    @Test
    public void testSearch() {
        WikiMediator searchwm = new WikiMediator(5, 100);
        Wiki searchwiki = new Wiki.Builder().withDomain("en.wikipedia.org").build();
        String query = "Barack Obama";
        List<String> answer1 = searchwiki.search(query, 0);
        List<String> myAnswer1 = searchwm.search("Barack Obama", 5);
        for(int i = 0; i < 5; i++) {
            System.out.println(answer1.get(i));
            System.out.println(myAnswer1.get(i));
            Assertions.assertTrue(answer1.contains(myAnswer1.get(i)));
        }
    }

    @Test
    public void testGetPage() {
        WikiMediator getwm = new WikiMediator(5, 100);
        Wiki searchwiki = new Wiki.Builder().withDomain("en.wikipedia.org").build();
        String pageTitle = "Barack Obama";
        String result = getwm.getPage(pageTitle);
        Assertions.assertEquals(result, searchwiki.getPageText(pageTitle));
        String result2 = getwm.getPage(pageTitle);
        Assertions.assertEquals(result, result2);
    }

    @Test
    public void testZeitgeist() {
        WikiMediator zeitwm = new WikiMediator(5, 100);
        zeitwm.getPage("Barack Obama");
        zeitwm.getPage("Lego");
        zeitwm.getPage("Barack Obama");
        zeitwm.getPage("Abraham Lincoln");
        zeitwm.getPage("Abraham Lincoln");
        zeitwm.search("Barack Obama", 2);
        zeitwm.search("Franklin D. Roosevelt", 5);
        zeitwm.search("Franklin D. Roosevelt", 5);
        zeitwm.search("garbage", 5);
        List<String> zeitexpected = new ArrayList<>();
        zeitexpected.add("Barack Obama");
        zeitexpected.add("Abraham Lincoln");
        Assertions.assertEquals(zeitexpected, zeitwm.zeitgeist(2));
    }

    @Test
    public void testTrending() {
        WikiMediator trendwm = new WikiMediator(5, 100);
        trendwm.getPage("Barack Obama");
        trendwm.getPage("Barack Obama");
        trendwm.getPage("Barack Obama");
        trendwm.getPage("Franklin D. Roosevelt");
        trendwm.getPage("Car");
        trendwm.getPage("Abraham Lincoln");
        try {
            Thread.sleep(3020);
        } catch (InterruptedException e) {
            // do nothing
        }
        trendwm.getPage("Franklin D. Roosevelt");
        trendwm.getPage("Barack Obama");
        trendwm.getPage("Barack Obama");
        trendwm.getPage("Abraham Lincoln");
        trendwm.getPage("Abraham Lincoln");
        trendwm.getPage("Abraham Lincoln");
        ArrayList<String> trendresult = new ArrayList<>();
        trendresult.add("Abraham Lincoln");
        trendresult.add("Barack Obama");
        trendresult.add("Franklin D. Roosevelt");
        Assertions.assertEquals(trendresult, trendwm.trending(2, 4));
    }

    @Test
    public void ServerTestSeveralClientsZietgeistSearch(){
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread(new Runnable(){
            @Override
            public void run() {
                try{
                    server=new WikiMediatorServer(9000, 2,
                            wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "one";
                    String type = "zeitgeist";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "search";
                    String query = "Barack Obama";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1", 9000);
                    String id = "three";
                    String type = "zeitgeist";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            Thread.sleep(101);
            clientThread2.start();
            Thread.sleep(101);
            clientThread3.start();
            clientThread.join();
            clientThread2.join();
            clientThread3.join();
        } catch (InterruptedException ioe) {
            // do nothing
        }
    }

    @Test
    public void serverTestSingleClientSearch(){
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread server = new Thread (new Runnable(){
            @Override
            public void run(){
                try {
                    WikiMediatorServer server =
                            new WikiMediatorServer(9000, 1, wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "zeitgeist";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch(IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        server.start();
        try {
            clientThread.start();
            clientThread.join();
        } catch (InterruptedException ioe) {
            //do nothing
        }
    }

    @Test
    public void ServerTestSeveralClientsGetPageZietgeist(){
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread (new Runnable(){
            @Override
            public void run(){
                try {
                    server = new WikiMediatorServer(9000, 4,
                            wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread (new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "one";
                    String type = "zeitgeist";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "getPage";
                    String pageTitle = "Abraham Lincoln";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("pageTitle", pageTitle);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1", 9000);
                    String id = "three";
                    String type = "zeitgeist";
                    String limit = "10";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            Thread.sleep(101);
            clientThread2.start();
            Thread.sleep(101);
            clientThread3.start();
            clientThread.join();
            clientThread2.join();
            clientThread3.join();
        }
        catch (InterruptedException ioe) {
            // do nothing
        }
    }

    @Test
    public void ServerTestSeveralClientsTrendingSearch(){
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread(new Runnable(){
            @Override
            public void run(){
                try {
                    server = new WikiMediatorServer(9000, 3, wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "one";
                    String type = "trending";
                    int timeLimitInSeconds = 2;
                    int maxItems = 2;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("timeLimitInSeconds", timeLimitInSeconds);
                    obj.addProperty("maxItems",maxItems);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "search";
                    String query = "Barack Obama";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "three";
                    String type = "search";
                    String query = "Korea";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread4 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "four";
                    String type = "search";
                    String query = "Taiwan";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread5 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "five";
                    String type = "search";
                    String query = "Canada";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe){
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread6 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "six";
                    String type = "search";
                    String query = "Canada";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread7 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "seven";
                    String type = "search";
                    String query = "Canada";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread8 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "eight";
                    String type = "search";
                    String query = "Barack Obama";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread9 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "nine";
                    String type = "trending";
                    int timeLimitInSeconds = 40;
                    int maxItems = 5;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("timeLimitInSeconds",
                            timeLimitInSeconds);
                    obj.addProperty("maxItems", maxItems);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            Thread.sleep(1000);
            clientThread2.start();
            clientThread3.start();
            clientThread4.start();
            clientThread5.start();
            clientThread6.start();
            clientThread7.start();
            clientThread8.start();
            Thread.sleep(1000);
            clientThread9.start();
            clientThread.join();
            clientThread2.join();
            clientThread3.join();
            clientThread4.join();
            clientThread5.join();
            clientThread6.join();
            clientThread7.join();
            clientThread8.join();
            clientThread9.join();
        }
        catch (InterruptedException ioe) {}
    }

    @Test
    public void ServerTestSeveralClientsTrendingSearchOutTimeWindow(){
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread (new Runnable(){
            @Override
            public void run(){
                try{
                    server = new WikiMediatorServer(9000, 15, wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "one";
                    String type = "trending";
                    int timeLimitInSeconds = 2;
                    int maxItems = 2;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("timeLimitInSeconds", timeLimitInSeconds);
                    obj.addProperty("maxItems",maxItems);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "search";
                    String query = "Banana";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "three";
                    String type = "trending";
                    int timeLimitInSeconds = 1;
                    int maxItems = 5;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type",type);
                    obj.addProperty("timeLimitInSeconds",
                            timeLimitInSeconds);
                    obj.addProperty("maxItems",maxItems);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            Thread.sleep(100);
            clientThread2.start();
            Thread.sleep(10000);
            clientThread3.start();
            clientThread.join();
            clientThread2.join();
            clientThread3.join();
        } catch (InterruptedException ioe) {
            // do nothing
        }
    }

    @Test
    public void ServerTestSeveralClientsShutdown(){
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread (new Runnable(){
            @Override
            public void run() {
                try{
                    server=new WikiMediatorServer(9000, 2, wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "one";
                    String type = "search";
                    String query = "Banana";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread2 = new Thread (new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "stop";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type",type);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "three";
                    String type = "search";
                    String query = "Banana";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type",type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            Thread.sleep(100);
            clientThread2.start();
            Thread.sleep(100);
            clientThread3.start();
            clientThread.join();
            clientThread2.join();
            clientThread3.join();
        } catch (InterruptedException ioe) {
            //do nothing
        }
    }

    @Test
    public void ServerTestSeveralClientsZietgeistSearchTimeout(){
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread (new Runnable(){
            @Override
            public void run(){
                try {
                    server = new WikiMediatorServer(9000, 2,
                            wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "one";
                    String type = "zeitgeist";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type",type);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread2 = new Thread (new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "search";
                    String query = "Philosophy";
                    String limit = "0";
                    int timeout = 1;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type",type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    obj.addProperty("timeout",timeout);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1", 9000);
                    String id = "three";
                    String type = "zeitgeist";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            Thread.sleep(101);
            clientThread2.start();
            Thread.sleep(101);
            clientThread3.start();
            clientThread.join();
            clientThread2.join();
            clientThread3.join();
        } catch (InterruptedException ioe) {}
    }

    @Test
    public void ServerTestSeveralClientsWindowPeak(){
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread(new Runnable(){
            @Override
            public void run(){
                try{
                    server = new WikiMediatorServer(9000, 4, wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread (new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "windowedPeakLoad";
                    int timeWindowInSeconds=5;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type",type);
                    obj.addProperty("timeWindowInSeconds",timeWindowInSeconds);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "search";
                    String query = "Barack Obama";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "three";
                    String type = "search";
                    String query = "Korea";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread4 = new Thread (new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "four";
                    String type = "search";
                    String query = "Taiwan";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread5 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "five";
                    String type = "search";
                    String query = "Canada";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread6 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "six";
                    String type = "search";
                    String query = "Canada";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread7 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "seven";
                    String type = "search";
                    String query = "Canada";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread8 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client =
                            new WikiMediatorClient("127.0.0.1",9000);
                    String id = "eight";
                    String type = "search";
                    String query = "Barack Obama";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread9=new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "nine";
                    String type = "windowedPeakLoad";
                    int timeWindowInSeconds = 5;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("timeWindowInSeconds",timeWindowInSeconds);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            Thread.sleep(100);
            clientThread2.start();
            clientThread3.start();
            clientThread4.start();
            Thread.sleep(6000);
            clientThread5.start();
            clientThread6.start();
            clientThread7.start();
            clientThread8.start();
            Thread.sleep(1000);
            clientThread9.start();
            clientThread.join();
            clientThread2.join();
            clientThread3.join();
            clientThread4.join();
            clientThread5.join();
            clientThread6.join();
            clientThread7.join();
            clientThread8.join();
            clientThread9.join();
        } catch (InterruptedException ioe) {
            // do nothing
        }
    }

    @Test
    public void ServerTestSeveralClientsWindowPeak30s(){
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    server = new WikiMediatorServer(9000, 4,
                            wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "windowedPeakLoad";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "search";
                    String query = "Barack Obama";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "three";
                    String type = "search";
                    String query = "Korea";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread4 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "four";
                    String type = "search";
                    String query = "Taiwan";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread5 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "five";
                    String type = "search";
                    String query = "Canada";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread6 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "six";
                    String type = "search";
                    String query = "Canada";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread7 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "seven";
                    String type = "search";
                    String query = "Canada";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread8 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "eight";
                    String type = "search";
                    String query = "Barack Obama";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread9 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "nine";
                    String type = "windowedPeakLoad";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            Thread.sleep(100);
            clientThread2.start();
            clientThread3.start();
            clientThread4.start();
            Thread.sleep(31000);
            clientThread5.start();
            clientThread6.start();
            clientThread7.start();
            clientThread8.start();
            Thread.sleep(1000);
            clientThread9.start();
            clientThread.join();
            clientThread2.join();
            clientThread3.join();
            clientThread4.join();
            clientThread5.join();
            clientThread6.join();
            clientThread7.join();
            clientThread8.join();
            clientThread9.join();
        }
        catch (InterruptedException ioe) {
            //do nothing
        }
    }

    @Test
    public void ServerTestSeveralClientsShortestPath() {
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server = new WikiMediatorServer(9000, 2,
                            wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1", 9000);
                    String id = "one";
                    String type = "shortestPath";
                    String pageTitle1 = "Academic bias";
                    String pageTitle2 = "Abraham Lincoln";
                    int timeout = 30;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("pageTitle1", pageTitle1);
                    obj.addProperty("pageTitle2", pageTitle2);
                    obj.addProperty("timeout", timeout);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1", 9000);
                    String id = "two";
                    String type = "shortestPath";
                    String pageTitle1 = "Philosophy";
                    String pageTitle2 = "Barack Obama";
                    int timeout = 30;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("pageTitle1", pageTitle1);
                    obj.addProperty("pageTitle2", pageTitle2);
                    obj.addProperty("timeout", timeout);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1", 9000);
                    String id = "three";
                    String type = "getPage";
                    String pageTitle = "Abraham Lincoln";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("pageTitle", pageTitle);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread4 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1", 9000);
                    String id = "four";
                    String type = "trending";
                    int timeLimitInSeconds = 100;
                    int maxItems = 2;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("timeLimitInSeconds", timeLimitInSeconds);
                    obj.addProperty("maxItems", maxItems);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            Thread.sleep(100);
            clientThread2.start();
            clientThread3.start();
            Thread.sleep(1000);
            clientThread4.start();
            clientThread.join();
            clientThread2.join();
            clientThread3.join();
            clientThread4.join();
        } catch (InterruptedException ioe) {
            //do nothing
        }
    }

    @Test
    public void ServerTestSeveralClientsShortestPathTimeout() {
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server = new WikiMediatorServer(9000, 2,
                            wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1", 9000);
                    String id = "one";
                    String type = "shortestPath";
                    String pageTitle1 = "Academic Bias";
                    String pageTitle2 = "Abraham Lincoln";
                    int timeout = 1;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("pageTitle1", pageTitle1);
                    obj.addProperty("pageTitle2", pageTitle2);
                    obj.addProperty("timeout", timeout);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            clientThread.join();
        } catch (InterruptedException ioe) {
            //do nothing
        }
    }

    @Test
    public void ServerTestSeveralClientsZeitgeistSearchInterruption() {

        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    server = new WikiMediatorServer(9000, 2,
                            wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "one";
                    String type = "zeitgeist";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "search";
                    String query = "Barack Obama";
                    String limit = "500";
                    int timeout = 1;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    obj.addProperty("timeout", timeout);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1", 9000);
                    String id = "three";
                    String type = "zeitgeist";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            Thread.sleep(101);
            clientThread2.start();
            Thread.sleep(101);
            clientThread3.start();
            clientThread.join();
            clientThread2.join();
            clientThread3.join();
        }
        catch (InterruptedException ioe) {
            // do nothing
        }
    }

    @Test
    public void ServerTestSeveralClientsShutdownAndRestart() {
        WikiMediator wiki = new WikiMediator(5, 100);
        Thread serverThread = new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    server = new WikiMediatorServer(9000, 2,
                            wiki);
                    server.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });
        WikiMediator wikinew = new WikiMediator(5,100);
        Thread serverThread1 = new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    WikiMediatorServer servernew = new WikiMediatorServer(9000, 2,
                            wikinew);
                    servernew.serve();
                } catch (RuntimeException ioe) {
                    throw new RuntimeException();
                }
            }
        });

        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "one";
                    String type = "search";
                    String query = "Banana";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "two";
                    String type = "stop";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply.toString());
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        Thread clientThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WikiMediatorClient client = new WikiMediatorClient("127.0.0.1",9000);
                    String id = "three";
                    String type = "search";
                    String query = "Sausage";
                    String limit = "5";
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", id);
                    obj.addProperty("type", type);
                    obj.addProperty("query", query);
                    obj.addProperty("limit", limit);
                    client.sendRequest(obj);
                    System.err.println(obj.toString());
                    String reply = client.getReply();
                    System.err.println("Reply!:" + reply);
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
        serverThread.start();
        try {
            clientThread.start();
            Thread.sleep(100);
            clientThread2.start();
            Thread.sleep(100);
            wikinew.readLocalHistory();
            serverThread1.start();
            Thread.sleep(1000);
            clientThread3.start();
            clientThread.join();
            clientThread2.join();
            clientThread3.join();
        } catch (InterruptedException ioe) {
            //do nothing
        }
    }

    @Test
    public void testWriteToDisk1() {
        File file = new File ("local/wikihistory.txt");
        file.delete();
        WikiMediator wm = new WikiMediator(5, 100);
        wm.getPage("Barack Obama");
        wm.search("Barack Obama", 3);
        wm.getPage("Barack Obama");
        wm.trending(5, 5);
        wm.getPage("Franklin D. Roosevelt");
        wm.getPage("Abraham Lincoln");
        wm.writeDataToDisk();
        Path p1 = Paths.get("local/wikihistory.txt");
        int ans = 0;
        try {
            String content = Files.readString(p1, StandardCharsets.US_ASCII);
            System.out.println(content);
            String[] lines = content.split("\r\n|\r|\n");
            ans = lines.length;
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertEquals(19, ans);
    }

    @Test
    public void testReadFromDisk1() {
        File file = new File ("local/wikihistory.txt");
        file.delete();
        WikiMediator wm1 = new WikiMediator(5, 100);
        wm1.getPage("Barack Obama");
        wm1.search("Barack Obama", 3);
        wm1.getPage("Barack Obama");
        wm1.trending(5, 5);
        wm1.getPage("Franklin D. Roosevelt");
        wm1.getPage("Abraham Lincoln");
        wm1.writeDataToDisk();
        WikiMediator wm = new WikiMediator(5, 100);
        wm.readLocalHistory();
        int testAns = wm.windowedPeakLoad(30);
        assertEquals(6, testAns);
        List<String> zeitexpected = new ArrayList<>();
        zeitexpected.add("Barack Obama");
        zeitexpected.add("Abraham Lincoln");
        Assertions.assertEquals(zeitexpected, wm.zeitgeist(2));
    }

    @Test
    public void testWindowedPeakLoad1() {
        File file = new File ("local/wikihistory.txt");
        file.delete();
        WikiMediator wm = new WikiMediator(5, 100);
        wm.getPage("Barack Obama");
        wm.search("Barack Obama", 3);
        wm.getPage("Barack Obama");
        wm.trending(5, 5);
        wm.getPage("Franklin D. Roosevelt");
        wm.getPage("Abraham Lincoln");
        try {
            wm.shortestPath("Philosophy", "Barack Obama", 60);
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
        int testAns = wm.windowedPeakLoad();
        assertEquals(8, testAns);
    }


    @Test
    public void testWindowedPeakLoad2() {
        File file = new File ("local/wikihistory.txt");
        file.delete();
        WikiMediator wm = new WikiMediator(5, 100);
        wm.getPage("Barack Obama");
        wm.search("Barack Obama", 3);
        wm.getPage("Korea");
        wm.search("Korea", 2);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // do nothing
        }
        wm.trending(5, 5);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // do nothing
        }
        wm.getPage("Franklin D. Roosevelt");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // do nothing
        }
        wm.getPage("Abraham Lincoln");
        int testAns = wm.windowedPeakLoad(2);
        assertEquals(4, testAns);
    }

    @Test
    public void testWindowedPeakLoad3() {
        File file = new File ("local/wikihistory.txt");
        file.delete();
        WikiMediator wm = new WikiMediator(5, 100);
        int testAns = wm.windowedPeakLoad(1);
        assertEquals(0, testAns);
    }


    @Test
    public void shortestPathTest1() {
        WikiMediator wikiM = new WikiMediator(300, 30);
        List<String> correctAns = new ArrayList<>();
        correctAns.add("Philosophy");
        correctAns.add("Academic bias");
        correctAns.add("Barack Obama");
        List<String> testAns = new ArrayList<>();
        try {
            testAns.addAll(wikiM.shortestPath("Philosophy", "Barack Obama", 60));
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
        assertEquals(correctAns, testAns);
    }

    @Test
    public void shortestPathTest2() {
        WikiMediator wikiM = new WikiMediator(300, 30);
        List<String> correctAns = new ArrayList<>();
        correctAns.add("Argentina");
        correctAns.add("Canada");
        correctAns.add("University of British Columbia");
        List<String> testAns = new ArrayList<>();
        try {
            testAns.addAll(wikiM.shortestPath("Argentina", "University of British Columbia",
                    900));
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
        assertEquals(correctAns, testAns);
    }

    @Test
    public void shortestPathTest3() {
        WikiMediator wikiM = new WikiMediator(300, 30);
        List<String> correctAns = new ArrayList<>();
        List<String> testAns = new ArrayList<>();
        try {
            testAns.addAll(wikiM.shortestPath("uclear Fusion", "Heterobathmia pseuderiocrania",
                    900));
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
        assertEquals(correctAns, testAns);
    }

    @Test
    public void shortestPathTest4() {
        WikiMediator wikiM = new WikiMediator(300, 30);
        List<String> correctAns = new ArrayList<>();
        correctAns.add("Black Hole");
        correctAns.add("Black Hole");
        List<String> testAns = new ArrayList<>();
        try {
            testAns.addAll(wikiM.shortestPath("Black Hole", "Black Hole", 900));
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
        assertEquals(correctAns, testAns);
    }

    @Test
    public void shortestPathTest5() {
        WikiMediator wikiM = new WikiMediator(300, 30);
        boolean timeoutE = false;
        try {
            wikiM.shortestPath("Cat", "Pyraminx", 10);
        } catch (TimeoutException e) {
            timeoutE = true;
            e.printStackTrace();
        }
        assertTrue(timeoutE);
    }

    @Test
    public void testManyThreadsAllWikiMediator() {
        File file = new File ("local/wikihistory.txt");
        file.delete();
        WikiMediator wikiMAll = new WikiMediator(300, 30);
        Thread wclient1 = new Thread(new Runnable() {
            public void run() {
                wikiMAll.search("Car", 1);
                wikiMAll.getPage("Car");
                try {
                    Thread.sleep(6030);
                } catch (InterruptedException ioe) {
                    // do nothing
                }
            }
        });
        Thread wclient2 = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(6030);
                } catch (InterruptedException ioe) {
                    // do nothing
                }
                wikiMAll.search("Barack Obama", 1);
                wikiMAll.getPage("Canada");
                wikiMAll.getPage("Canada");
            }
        });
        Thread wclient3 = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(6030);
                } catch (InterruptedException ioe) {
                    // do nothing
                }
                wikiMAll.search("Goose", 1);
                wikiMAll.getPage("Goose");
                wikiMAll.getPage("Goose");
                wikiMAll.getPage("Goose");
            }
        });
        try {
            wclient1.start();
            wclient2.start();
            wclient3.start();
            Thread.sleep(7010);
            List<String> allResults = new ArrayList<>();
            allResults.add("Goose");
            allResults.add("Canada");
            allResults.add("Barack Obama");
            Assertions.assertEquals(allResults, wikiMAll.trending(3, 4));
        } catch (InterruptedException ioe) {
            // do nothing
        }
    }
}
