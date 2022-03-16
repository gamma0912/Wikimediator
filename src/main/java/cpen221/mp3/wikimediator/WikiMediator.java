package cpen221.mp3.wikimediator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.io.*;
import cpen221.mp3.fsftbuffer.FSFTBuffer;
import cpen221.mp3.fsftbuffer.Website;
import org.fastily.jwiki.core.Wiki;


public class WikiMediator {

    /**
     * Rep Invariant
     *
     * - 'zeitrequests' and 'requests' and 'cache' are not null and don't contain
     *    null elements
     * - 'wiki' is not null has access to webpages of Wikipedia
     * - 'zeitrequests' is updated whenever search and getPage are called
     * - 'requests' is updated whenever a method in this class is called
     * - OUTPUTFILEPATH is not null
     *
     * --------------------------------------------------
     *
     * Abstraction Function
     *
     * A WikiMediator object is an interface between the client and Wikipedia,
     * with a 'cache' storing recent 'getPage' results and an instance of 'wiki' to
     * access Wikipedia. It allows clients to search for related pages, get page text,
     * see popular searches, and see when traffic was highest in a time window, and find the
     * shortest path between two Wikipedia pages. 'zeitrequests' records queries used to search
     * for related pages and get page text, and 'requests' stores information on all calls to
     * Wikimediator. 'OUTPUTFILEPATH' is where access history will be stored locally.
     *
     * ---------------------------------------------------
     *
     * Thread Safety Argument
     *
     * This class is thread-safe because it is synchonized:
     * All method bodies are wrapped in synchronized blocks. Also, global fields are
     * final.
     */

    private final Wiki wiki;
    private final FSFTBuffer<Website> cache;
    private final Map<String, List<Long>> zeitrequests = new HashMap<>();
    private final Map<String, List<Long>> requests = new HashMap<>();
    final static String OUTPUTFILEPATH = "local/wikihistory.txt";

    /**
     * Create a WikiMediator with a buffer size and element timeout value.
     * Requires: capacity > 0 & stalenessInterval > 0
     * @param capacity the max size of the buffer
     * @param stalenessInterval time, in seconds, when elements in the buffer become stale
     */
    public WikiMediator(int capacity, int stalenessInterval) {
        cache = new FSFTBuffer(capacity, stalenessInterval);
        wiki = new Wiki.Builder().withDomain("en.wikipedia.org").build();
        if (new File(OUTPUTFILEPATH).isFile()) {
            readLocalHistory();
        }
    }

    /**
     * Given a query, return up to limit page titles that match the query string (per Wikipedia's search service).
     * Requires: query is not null
     * Modifies: updates access history by adding to zeitrequests and requests
     * @param query the argument to search for
     * @param limit the max number of results returned, if limit <= 0 then returns all related page titles
     * @return a list of strings containing page titles that match the query
     */
    public List<String> search(String query, int limit) {
        synchronized (this) {
            long searchTime = System.currentTimeMillis();
            if (!zeitrequests.containsKey(query)) {
                zeitrequests.put(query, new ArrayList<>());
            }
            zeitrequests.get(query).add(searchTime);
            if (!requests.containsKey(query)) {
                requests.put(query, new ArrayList<>());
            }
            requests.get(query).add(searchTime);
            List<String> result = new ArrayList<>();
            result = wiki.search(query, limit);
            return result;
        }
    }

    /**
     * Given a pageTitle, return the text associated with the Wikipedia page that matches pageTitle.
     * If the pageTitle is invalid, an empty string is returned.
     * Requires: pageTitle is not null
     * Modifies: updates access history by adding to zeitrequests and requests
     * @param pageTitle the title of the Wikipedia page to get text from
     * @return the text body of the page, empty string if page is not found
     */
    public String getPage(String pageTitle) {
        synchronized (this) {
            long getPageTime = System.currentTimeMillis();
            if (!zeitrequests.containsKey(pageTitle)) {
                zeitrequests.put(pageTitle, new ArrayList<>());
            }
            zeitrequests.get(pageTitle).add(getPageTime);
            if (!requests.containsKey(pageTitle)) {
                requests.put(pageTitle, new ArrayList<>());
            }
            requests.get(pageTitle).add(getPageTime);
            try {
                return cache.get(pageTitle).getContent();
            }
            catch (NoSuchElementException e) {
                String text = wiki.getPageText(pageTitle);
                Website w = new Website(pageTitle, text);
                cache.put(w);
                return text;
            }
        }
    }

    /**
     * Return the most common Strings used in search and getPage requests,
     * with items being sorted in non-increasing count order.
     * Requires: limit > 0
     * Modifies: updates access history by adding to requests
     * @param limit the max number of Strings returned
     * @return a list of Strings most commonly used in search and getPage requests
     */
    public List<String> zeitgeist(int limit) {
        synchronized (this) {
            long zeitTime = System.currentTimeMillis();
            if (!requests.containsKey("")) {
                requests.put("", new ArrayList<>());
            }
            requests.get("").add(zeitTime);
            List<String> popular =
                    zeitrequests.entrySet().stream()
                            .sorted(Comparator.comparingInt(entry -> entry.getValue().size()))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
            List<String> zeitgeistResult = new ArrayList<>();
            Collections.reverse(popular);
            for (int i = 0; i < Math.min(popular.size(), limit); i++) {
                zeitgeistResult.add(popular.get(i));
            }
            return zeitgeistResult;
        }
    }

    /**
     * Return the most common Strings used in search and getPage requests made in the last timeLimitInSeconds seconds
     * with items being sorted in non-increasing count order
     * Requires: timeLimitInSeconds and maxItems are > 0
     * Modifies: updates access history by adding to requests
     * @param timeLimitInSeconds how long ago in the past to start searching from
     * @param maxItems the max number of results
     * @return a list of Strings containing results
     */
    public List<String> trending(int timeLimitInSeconds, int maxItems) {
        synchronized (this) {
            long trendTime = System.currentTimeMillis();
            if (!requests.containsKey("")) {
                requests.put("", new ArrayList<>());
            }
            requests.get("").add(trendTime);
            Map<String, List<Long>> trendingMap = new HashMap<>();
            for (Map.Entry<String, List<Long>> entry : zeitrequests.entrySet()) {
                for (int i = 0; i < entry.getValue().size(); i++) {
                    if (trendTime - entry.getValue().get(i) <=
                            timeLimitInSeconds * 1000) {
                        if (!trendingMap.containsKey(entry.getKey())) {
                            trendingMap.put(entry.getKey(), new ArrayList<Long>());
                        }
                        else {
                            trendingMap.get(entry.getKey()).add(entry.getValue().get(i));
                        }
                    }
                }
            }
            List<String> trending =
                    trendingMap.entrySet().stream()
                            .sorted(Comparator.comparingInt(entry ->
                                    entry.getValue().size()))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
            Collections.reverse(trending);
            List<String> trendingResult = new ArrayList<>();
            for (int i = 0; i < Math.min(trending.size(), maxItems); i++) {
                trendingResult.add(trending.get(i));
            }
            return trendingResult;
        }
    }


    /**
     * Returns the most maximum number of requests seen in any time for a given
     * window length, does not modify requests.
     *
     * Requires: timeWindowInSeconds > 0
     * @param timeWindowInSeconds the time window in which requests are searched for
     * @return the maximum number of requests for the given time window
     */
    public int windowedPeakLoad(int timeWindowInSeconds) {
        synchronized (this) {
            List<Long> instantList = new ArrayList<>();
            for (Map.Entry<String, List<Long>> entry : requests.entrySet()){
                instantList.addAll(entry.getValue());
            }
            if (instantList.size() == 0) {
                return 0;
            }
            Collections.sort(instantList);
            int marker = 0;
            int maxCount = 0;
            boolean enableMarker;
            for (int i = 0; i-1 < instantList.size(); i++) {
                enableMarker = true;
                int j = i+1;
                while ((j < instantList.size()) &&
                        (instantList.get(j) - instantList.get(i) <
                                (long) timeWindowInSeconds * 1000) ){
                    if ((instantList.get(j) - instantList.get(i) > 0) &&
                            enableMarker){
                        marker = j;
                        enableMarker = false;
                    }
                    j++;
                }
                if (j-i > maxCount) {
                    maxCount = j - i ;
                }
                if (!enableMarker) {
                    i = marker - 1;
                }
            }
            return maxCount;
        }
    }

    /**
     * Returns the most maximum number of requests seen in any time for a window
     * length of 30s, does not modify requests.
     *
     * @return the maximum number of requests in a 30 s period
     */
    public int windowedPeakLoad() {
        synchronized (this) {
            return windowedPeakLoad(30);
        }
    }

    /**
     * Returns the lexicographically smallest shortest path between 2 Wikipedia
     * pages by links on a Wikipedia page.
     * Requires: pageTitle1 to be a valid wikipedia page
     * Requires: pageTitle2 to be a valid wikipedia page
     * Requires: timeout to be > 0
     * @param pageTitle1 the Wikipedia page to start from
     * @param pageTitle2 the Wikipedia page to look for
     * @param timeout time in seconds to search for the destination page before
     *                a timeout exception is thrown
     * @return A List of the order of pages to get from pageTitle1 to pageTitle2,
     *         returns an empty list if no such path exists
     * @throws TimeoutException if the path is not found within the timeout limit,
     *                          a TimeoutException is thrown
     */
    public List<String> shortestPath(String pageTitle1, String pageTitle2, int timeout) throws TimeoutException {
        synchronized (this){
            long startTime = System.currentTimeMillis();
            if (!requests.containsKey(pageTitle1)) {
                requests.put(pageTitle1, new ArrayList<>());
            }
            requests.get(pageTitle1).add(startTime);
            if (!requests.containsKey(pageTitle2)) {
                requests.put(pageTitle2, new ArrayList<>());
            }
            requests.get(pageTitle2).add(startTime);
            if (pageTitle1.equals(pageTitle2)) {
                List<String> returnList = new ArrayList<>();
                returnList.add(pageTitle1);
                returnList.add(pageTitle2);
                return returnList;
            }
        boolean pathExists = false;
        Queue<String> toSearchQ = new LinkedBlockingQueue<>();
        Map<String, String> childParentLink = new ConcurrentHashMap<>();
        toSearchQ.add(pageTitle1);
        childParentLink.put(pageTitle1, pageTitle1);
            while (!pathExists && !toSearchQ.isEmpty()) {
                if (System.currentTimeMillis() - startTime > (timeout * 1000)) {
                    throw new TimeoutException();
                }
                String searchString = toSearchQ.remove();
                List<String> nextPageLinks = this.wiki.getLinksOnPage(searchString);
                for (String i : nextPageLinks) {
                    if (!childParentLink.containsKey(i)) {
                        toSearchQ.add(i);
                        childParentLink.put(i, searchString);
                    }
                    if (i.equals(pageTitle2)) {
                        pathExists = true;
                        break;
                    }
                }
            }
            if (!pathExists) {
                List<String> returnList = new ArrayList<>();
                return returnList;
            } else {
                LinkedList<String> returnList = new LinkedList<>();
                returnList.push(pageTitle2);
                String parentLink = pageTitle2;
                while (!returnList.get(0).equals(pageTitle1)) {
                    returnList.push(childParentLink.get(parentLink));
                    parentLink = childParentLink.get(parentLink);
                }
                List<String> convertedReturnList = new ArrayList<>();
                convertedReturnList.addAll(returnList);
                return convertedReturnList;
            }
        }
    }

    /**
     * Saves instance history data from the server and stores in a txt file in
     * OUTPUTFILEPATH so server can restore state when restarted.
     * Modifies: creates a file called wikihistory.txt in OUTPUTFILEPATH or
     *           replaces an existing file with the same name.
     */
    public void writeDataToDisk() {
        BufferedWriter bw = null;
        try {
            // create new BufferedWriter for the output file
            bw = new BufferedWriter(new FileWriter(OUTPUTFILEPATH));
            // iterate map entries
            for (Map.Entry<String, List<Long>> entry : requests.entrySet()) {
                // put key and value separated by a colon
                bw.write(entry.getKey() + ":\n");
                for (int i = 0; i < entry.getValue().size(); i++) {
                    bw.write(String.valueOf(entry.getValue().get(i)) + "\n");
                }
            }
            bw.write("~~~\n");
            // iterate map entries
            for (Map.Entry<String, List<Long>> entry : zeitrequests.entrySet()) {
                // put key and value separated by a colon
                bw.write(entry.getKey() + ":\n");
                for (int i = 0; i < entry.getValue().size(); i++) {
                    bw.write(String.valueOf(entry.getValue().get(i)) + "\n");
                }
            }
            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                // always close the writer
                bw.close();
            }
            catch (Exception e) {
                //do nothing
            }
        }
    }

    /**
     * Loads instance history from a txt file and loads the data to the current
     * instance of WikiMediator.
     */
    public void readLocalHistory() {
        try {
            BufferedReader br = new BufferedReader(new FileReader(OUTPUTFILEPATH));
            String line;
            String key = "";
            boolean zeitToggle = false;
            while ((line = br.readLine()) != null) {
                if (zeitToggle == false) {
                    if (line.equals("~~~")) {
                        zeitToggle = true;
                    } else if (line.endsWith(":")) {
                        key = line.substring(0, line.length() - 1);
                        requests.put(key, new ArrayList<Long>());
                    } else {
                        requests.get(key).add(Long.parseLong(line));
                    }
                } else {
                    if (line.endsWith(":")) {
                        key = line.substring(0, line.length() - 1);
                        zeitrequests.put(key, new ArrayList<Long>());
                    } else {
                        zeitrequests.get(key).add(Long.parseLong(line));
                    }
                }
            }
            br.close();
        }
        catch (FileNotFoundException e) {
            //do nothing
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
