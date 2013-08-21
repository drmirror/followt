package net.followt;

import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

/**
 * Process that scans the followers of twitter users and
 * updates their follower history in the database.
 * @author drmirror
 */
public class Scanner implements Runnable {

    private Twitter twitter = Twitter.getInstance();
    private UserDB  userDB  = UserDB.getInstance();
    
    private DBCollection fscans = null;
    private DBCollection fcurrent = null;
    private DBCollection fhistory = null;
    
    private SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
    
    public Scanner() {
        initMongo();
    }

    private void initMongo() {
        try {
            MongoClient client = new MongoClient();
            DB db = client.getDB("followt");
            fscans = db.getCollection("fscans");
            fcurrent = db.getCollection("fcurrent");
            fhistory = db.getCollection("fhistory");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
    
    private class Scan {
        public int user_id;
        public Date scan_started;
        public long next_cursor = -1;
        public Date scan_ended;
        public Scan (DBObject source) {
            this.user_id = (Integer)source.get("user_id");
            this.scan_started = (Date)source.get("scan_started");
            if (source.containsField("next_cursor"))
                next_cursor = (long)source.get("next_cursor");
            this.scan_ended = (Date)source.get("scan_ended");
        }
        public void update() {
            BasicDBObject o = new BasicDBObject("user_id",user_id)
                                        .append("scan_started",scan_started);
            if (next_cursor > 0) o.append("next_cursor", next_cursor);
            if (scan_ended != null) o.append("scan_ended", scan_ended);
            fscans.update(new BasicDBObject("user_id",user_id),
                          o, true, false);
        }
    }
    
    @Override
    public void run() {
        try {
            long startTime = System.currentTimeMillis();
            // get user to scan
            Scan currentScan = nextScan();
            if (currentScan == null) return;
            System.out.print(df.format(new Date()) + " scanning "
                    + userDB.getScreenName(currentScan.user_id) + " ("
                    + currentScan.user_id + ") ...");

            if (currentScan.next_cursor <= 0) { // this is the start of the scan
                fcurrent.remove(new BasicDBObject("followee", currentScan.user_id));
                currentScan.scan_started = new Date();
                currentScan.scan_ended = null;
                currentScan.next_cursor = -1;
                currentScan.update();
            }

            List<Integer> followers = new ArrayList<Integer>();
            long next_cursor = twitter.getFollowerBatch(currentScan.user_id,
                                                        currentScan.next_cursor, followers);

            // insert_fcurrent(currentScan.user_id, followers);
            insert_fhistory_positive(currentScan.user_id, followers);
            if (next_cursor <= 0) { // scan completed
                insert_fhistory_negative(currentScan.user_id);
            }

            currentScan.scan_ended = new Date();
            currentScan.next_cursor = next_cursor;
            currentScan.update();
            long time = System.currentTimeMillis() - startTime;
            if (next_cursor > 0)
                System.out.println(" to be continued (" + time + ")");
            else
                System.out.println(" done (" + time + ")");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private Scan nextScan() {
        DBCursor c = fscans.find()
                           .sort(new BasicDBObject("next_cursor",-1)
                                           .append("scan_ended",1));
        if (c.hasNext()) {
            return new Scan(c.next());
        } else {
            return null;
        }
    }
    
    //private void insert_fcurrent(int followee, List<Integer> followers) {
    //    for (int follower : followers) {
    //        fcurrent.insert(new BasicDBObject("followee",followee)
    //                                  .append("follower",follower));
    //    }
    //}
    
    private void insert_fhistory_positive(int followee, List<Integer> followers) {
        for (int follower : followers) {
            List<DBObject> followHistory = fhistory.find(
                new BasicDBObject("followee",followee)
                          .append("follower",follower))
                .sort(new BasicDBObject("end",1)).toArray();
            Date now = new Date();
            if (followHistory.size() == 0 || followHistory.get(0).get("end") != null) {
                // not currently following that user: create a new entry
                DBObject entry = new BasicDBObject("followee",followee)
                                           .append("follower",follower)
                                           .append("start",now)
                                           .append("last",now);
                fhistory.insert(entry);
            } else if (followHistory.size() > 0) {
                // been following that user already: update "last" timestamp
                DBObject firstEntry = followHistory.get(0);
                firstEntry.put("last",now);
                fhistory.update(new BasicDBObject("_id",firstEntry.get("_id")), firstEntry);
            }
        }
    }
    
    
    private void insert_fhistory_negative (int followee) {
        DBObject fscan = fscans.find(new BasicDBObject("user_id",followee)).toArray().get(0);
        Scan s = new Scan(fscan);
        fhistory.update(new BasicDBObject("followee",followee)
                                  .append("end",new BasicDBObject("$exists",false))
                                  .append("last",new BasicDBObject("$lt",s.scan_started)),
                        new BasicDBObject("$set",new BasicDBObject("end",new Date())),
                        false, true);
    }
    
//    private void insert_fhistory_negative_old(int followee) {
//        DBCursor c = fhistory.find(new BasicDBObject("followee",followee)
//                                             .append("end",null));
//        for (DBObject o : c) {
//            int follower = (int)o.get("follower");
//            DBCursor x = fcurrent.find(new BasicDBObject("followee",followee)
//                                                 .append("follower",follower));
//            if (!x.hasNext()) {
//                BasicDBObject b = (BasicDBObject)o;
//                b.put ("end",new Date());
//                fhistory.update(new BasicDBObject("_id",b.get("_id")),b);
//            }
//        }
//    }
    
    public void startMonitoring (String screenName) {
        int user_id = twitter.getId(screenName);
        if (!fscans.find(new BasicDBObject("user_id",user_id)).hasNext())
            fscans.insert(new BasicDBObject("user_id",user_id));
    }
    
    public static void main(String[] args) {
        Scanner s = new Scanner();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(s, 0, 60, TimeUnit.SECONDS);
    }
}
