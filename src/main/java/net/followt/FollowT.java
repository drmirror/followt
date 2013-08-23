package net.followt;

import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.mongodb.AggregationOutput;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

/**
 * Provides access to the follower histories of Twitter users.
 * @author drmirror
 */
public class FollowT {

    private UserDB userDB = UserDB.getInstance();
    private DBCollection fhistory = null;
    
    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    private FollowT() {
        initMongo();
    }
    
    private void initMongo() {
        try {
            MongoClient client = new MongoClient();
            DB db = client.getDB("followt");
            fhistory = db.getCollection("fhistory");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Represents a user that started following another user
     * at some point in the past.
     */
    public class Follower {
        public int followee;
        public int follower;
        public String followerScreenName;
        public Date followedSince;
        public Date lastSeen;
        public Follower (BasicDBObject source) {
            this.followee = source.getInt("followee");
            this.follower = source.getInt("follower");
            this.followedSince = source.getDate("start");
            this.lastSeen = source.getDate("last");
        }
        public int getFollowee() {
            return followee;
        }
        public int getFollower() {
            return follower;
        }
        public String getFollowerScreenName() {
            if (followerScreenName == null) {
                followerScreenName = userDB.getScreenName(follower);
            }
            return followerScreenName;
        }
        public Date getFollowedSince() {
            return followedSince;
        }
        public Date getLastSeen() {
            return lastSeen;
        }
    }

    /**
     * A special follower who, after starting to follow another user,
     * has since stopped following that user again.
     */
    public class Unfollower extends Follower {
        public Date unfollowedSince;
        public Unfollower (BasicDBObject source) {
            super (source);
            this.unfollowedSince = source.getDate("end");
        }
        public Date getUnfollowedSince() {
            return unfollowedSince;
        }
        public long getFollowingFor() {
            return unfollowedSince.getTime() - followedSince.getTime();
        }
        public String toString() {
            String screenName = userDB.getScreenName(follower);
            double followTime = (unfollowedSince.getTime() - followedSince.getTime()) / 86400000.0;
            return String.format ("%s %4.2f %s", df.format(unfollowedSince), followTime, screenName);
        }
    }
    
    /**
     * Returns the list of users which have unfollowed the given user in the
     * period between now and <code>interval</code> milliseconds in the past. 
     * @param screenName the name of the user for which unfollowers should be returned
     * @param interval number of milliseconds between now and the point in the past
     * from which unfollowers should be returned
     * @return the list of users who have unfollowed the given user
     */
    public List<Unfollower> getRecentUnfollowers(String screenName, long interval) {
        int followee = userDB.getId(screenName);
        Date cutoff = new Date(System.currentTimeMillis() - interval);
        List<Unfollower> result = new ArrayList<Unfollower>();
        DBCursor dbresult = fhistory.find(
            new BasicDBObject("followee",followee)
                      .append("end",new BasicDBObject("$gte", cutoff))
        ).sort(new BasicDBObject("end",1));
        List<Integer> unfollowerIds = new ArrayList<Integer>();
        for (DBObject o : dbresult) {
            unfollowerIds.add (((BasicDBObject)o).getInt("follower"));
        }
        userDB.lookupUsers(unfollowerIds);
        for (DBObject o : dbresult) {
            Unfollower u = new Unfollower((BasicDBObject)o);
            result.add(u);
        }
        return result;
    }
    
    /**
     * Returns the point in time when followt started monitoring the given user.
     * @param followee the user for which the beginning of time should be returned
     * @return point in time when the first scan of that user started
     */
    public Date beginningOfTime (int followee) {
        AggregationOutput agr = fhistory.aggregate(
            new BasicDBObject("$match",
                new BasicDBObject ("followee",followee)),
            new BasicDBObject("$group",
                new BasicDBObject("_id",null)
                          .append("beginning_of_time", 
                                  new BasicDBObject("$min","$start"))));
        DBObject result = agr.results().iterator().next();
        return (Date)result.get("beginning_of_time");
    }
    
    public Date beginningOfTime (String screenName) {
        int id = userDB.getId(screenName);
        return beginningOfTime(id);
    }
    
    /**
     * Returns the list of users which have started following the given user in the
     * period between now and <code>interval</code> milliseconds in the past. 
     * @param screenName the name of the user for which new followers should be returned
     * @param interval number of milliseconds between now and the point in the past
     * from which new followers should be returned
     * @return the list of users who have started following the given user
     */
    public List<Follower> getRecentFollowers(String screenName, long interval) {
        int followee = userDB.getId(screenName);
        Date beginningOfTime = beginningOfTime (followee);
        Date cutoff = new Date(System.currentTimeMillis() - interval);
        if (cutoff.getTime() < beginningOfTime.getTime()) {
            cutoff = new Date (beginningOfTime.getTime() + 3600000);
        }
        List<Follower> result = new ArrayList<Follower>();
        DBCursor dbresult = fhistory.find(
            new BasicDBObject("followee",followee)
                      .append("start",new BasicDBObject("$gte", cutoff))
        ).sort(new BasicDBObject("start",1));
        List<Integer> followerIds = new ArrayList<Integer>();
        for (DBObject o : dbresult) {
            followerIds.add (((BasicDBObject)o).getInt("follower"));
        }
        userDB.lookupUsers(followerIds);
        for (DBObject o : dbresult) {
            Follower f = new Follower((BasicDBObject)o);
            result.add(f);
        }
        return result;
    }
    
    public int newFollowersOnDay (String screenName, Date d) {
        int followee = userDB.getId(screenName);
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        Date start = c.getTime();
        Date end = new Date(c.getTimeInMillis() + 86400000);
        int result = fhistory.find(new BasicDBObject("followee", followee)
                                             .append("start", new BasicDBObject ("$gte", start)
                                                                         .append("$lt", end))).count();
        return result;                             
    }

    public int lostFollowersOnDay (String screenName, Date d) {
        int followee = userDB.getId(screenName);
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        Date start = c.getTime();
        Date end = new Date(c.getTimeInMillis() + 86400000);
        int result = fhistory.find(new BasicDBObject("followee", followee)
                                             .append("end", new BasicDBObject ("$gte", start)
                                                                       .append("$lt", end))).count();
        return result;                             
    }

    private static FollowT instance = null;
    
    public static FollowT getInstance() {
        if (instance == null) instance = new FollowT();
        return instance;
    }
    
    public static void main(String[] args) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String screenName = "db2";
        FollowT f = getInstance();
        Date bot = f.beginningOfTime(screenName);
        Calendar c = Calendar.getInstance();
        c.setTime(bot);
        while (c.getTimeInMillis() < System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_MONTH, 1);
            int newFollowers = f.newFollowersOnDay(screenName, c.getTime());
            int lostFollowers = f.lostFollowersOnDay(screenName, c.getTime());
            System.out.format("%s %3d %3d\n", df.format(c.getTime()), newFollowers, lostFollowers);
        }
    }
    
}
