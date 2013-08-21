package net.followt;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

/**
 * A cache that maps numeric user ids of Twitter users
 * to their screen names.  The mapping is stored
 * persistently in a MongoDB collection.
 * <p>
 * Since round-trips to Twitter take quite long, and
 * we can only resolve 100 uids at a time (and perform
 * 180 of these queries per 15 min time window), this
 * class resolves uids asynchronously and in batches
 * up to 100 uids.
 * 
 * @author drmirror
 */
public class UserDB {
    
    private Twitter twitter = Twitter.getInstance();
    private DBCollection userCollection = null;
    private BlockingQueue<Integer> lookupQueue = new LinkedBlockingQueue<Integer>();

    /**
     * A lock that allows other threads to wait until
     * new id/screen names have been written to the
     * database.
     */
    private Object dbLock = new Object();
    
    /**
     * Continuously running thread that takes requests from the
     * <code>lookupQueue</code>, batches them into groups of
     * up to 100, and passes them to Twitter for resolution.
     * Results are written to the database, from where clients
     * calling other methods of this class pick them up.
     */
    private Thread lookupThread = new Thread (new Runnable() {
        public void run() {
            List<Integer> batch = new LinkedList<Integer>();
            while (true) {
                batch.clear();
                try {
                    int first = -1;
                    try {
                        first = lookupQueue.take();
                    } catch (InterruptedException ex) {
                        continue;
                    }
                    batch.add(first);
                    try { Thread.sleep(100); } catch (InterruptedException ex) {}
                    lookupQueue.drainTo(batch, Twitter.MAX_LOOKUPS - 1);
                    Map<Integer,String> result = twitter.lookupIds(batch);
                    fixResult (result, batch);
                    synchronized(dbLock) {
                        insertUsers (result);
                        dbLock.notifyAll();
                    }    
                } catch (TwitterException ex) {
                    System.out.println(ex);
                    synchronized(dbLock) {
                        insertFailures (batch);
                        dbLock.notifyAll();
                    }
                }
            }
            
        }
    });

    /**
     * If we have asked for a certain user id, but have not received its screen name
     * from Twitter, mark it as a non-resolvable user id (*uid*) to make sure we
     * don't query it over and over again. 
     * @param result
     * @param batch
     */
    private void fixResult (Map<Integer,String> result, List<Integer> batch) {
        for (int id : batch) {
            if (result.get(id) == null) {
                result.put(id, "*" + id + "*");
            }
        }
    }
    
    private void insertUsers (Map<Integer,String> users) {
        for (Map.Entry<Integer,String> e : users.entrySet()) {
            getUserCollection().update(
                    new BasicDBObject("_id", e.getKey()),
                    new BasicDBObject("_id", e.getKey()).append("screen_name", e.getValue()),
                    true, false);
        }
    }
    
    private void insertFailures (List<Integer> ids) {
        for (int id : ids) {
            getUserCollection().update(
                    new BasicDBObject("_id", id),
                    new BasicDBObject("_id", id).append("screen_name", "*" + id + "*"),
                    true, false);
        }
    }
    
    private UserDB() {
        lookupThread.setDaemon(true);
        lookupThread.start();
    }
    
    private static UserDB instance = null;
    
    public static UserDB getInstance() {
        if (instance == null) instance = new UserDB();
        return instance;
    }
    
    private DBCollection getUserCollection() {
        if (userCollection == null) {
            try {
                MongoClient client = new MongoClient();
                DB db = client.getDB("followt");
                userCollection = db.getCollection("users");
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }
        return userCollection;
    }
    
    private String getScreenNameFromDB (int id) {
        String result = null;
        synchronized(dbLock) {
            DBCursor c = getUserCollection().find(
                new BasicDBObject("_id", id)
            );
            if (c.hasNext()) {
                result = (String)((DBObject)c.next()).get("screen_name");
            }
        }
        return result;
    }
    
    public void lookupUsers (List<Integer> ids) {
        for (int id : ids) {
            if (getScreenNameFromDB(id) == null) {
                if (!lookupQueue.contains(id)) {
                    lookupQueue.add(id);
                }
            }
        }
    }

    public void lookupUsers (int[] ids) {
        List<Integer> args = new ArrayList<Integer>();
        for (int id : ids) args.add(id);
        lookupUsers(args);
    }
    
    public String getScreenName (int id) {
        String result = null;
        while (true) {
            result = getScreenNameFromDB(id);
            if (result != null) {
                break;   
            }
            if (!lookupQueue.contains(id)) {
                lookupQueue.add(id);
                synchronized(dbLock) {
                    try {
                        dbLock.wait(10000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return result;
    }
    
    public int getId (String screenName) {
        int result = -1;
        DBCursor c = getUserCollection().find(
            new BasicDBObject("screen_name", screenName)
        );
        if (!c.hasNext()) {
            result = twitter.getId(screenName);
            getUserCollection().insert(new BasicDBObject("_id", result)
                                                 .append("screen_name",screenName));
        } else {
            DBObject o = c.next();
            result = (Integer)o.get("_id");
        }
        return result;
    }
    
    public static void main (String[] args) throws Exception {
        UserDB db = getInstance();
        System.out.println(db.getId("KlingebielJens"));
    }

}
