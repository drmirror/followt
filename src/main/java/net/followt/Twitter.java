package net.followt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.TwitterApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

/**
 * Provides the interface for sending requests to Twitter and receiving responses.
 * There is a low-level method, request(String), which can send arbitrary requests,
 * and several higher-level methods such as getId() or getScreenName().
 * <p>
 * This class does not perform any caching by itself; it only encapsulates
 * the actual requests to the Twitter API.
 * <p>
 * For authorization, a file twitter.properties must be provided with four
 * values, api_key, api_secret, access_token, and access_secret. These values
 * can be obtained by registering on Twitter's developer website.
 * 
 * @author drmirror
 */
public class Twitter {

    public final static int MAX_LOOKUPS = 100;
    public final static int MAX_FOLLOWER_BATCHES = 15;
    
    private static OAuthService oauthService;
    private static Token accessToken;
    
    private static Twitter instance = null;
    
    /**
     * Private constructor to enforce singleton pattern.  Use Twitter.getInstance()
     * to retrieve the only object of this class.
     */
    private Twitter (String api_key, String api_secret,
                     String access_token, String access_secret) {
        oauthService = new ServiceBuilder()
            .provider(TwitterApi.class)
            .apiKey(api_key)
            .apiSecret(api_secret)
            .build();
        accessToken = new Token (access_token, access_secret);
    }
    
    /**
     * Sends a single GET request to Twitter by appending the given command to
     * the URL "https://api.twitter.com/1.1/".  The JSON response is parsed
     * into a DBObject and returned. 
     * @param command
     * @return the parsed JSON response
     * @throws TwitterException or subtype thereof
     */
    public DBObject request (String command) {

        OAuthRequest request = new OAuthRequest(Verb.GET, "https://api.twitter.com/1.1/" + command);
        oauthService.signRequest(accessToken, request);
        Response response = request.send();
        if (!response.isSuccessful()) {
            throw TwitterException.create(response);
        }
        DBObject result = (DBObject)JSON.parse(response.getBody());
        return result;
        
    }
    
    /**
     * Translates a batch of numeric user ids into screen names,
     * by looking them up via the Twitter API.  The list may not
     * be longer than MAX_LOOKUPS users (currently 100).
     * @param ids the user ids to be looked up
     * @return a map from user ids to the corresponding screen names
     */
    public Map<Integer,String> lookupIds (List<Integer> ids) {
        if (ids == null) throw new IllegalArgumentException();
        int[] args = new int[ids.size()];
        for (int i=0; i<ids.size(); i++) {
           args[i] = ids.get(i);
        }
        return lookupIds(args);
    }
    
    /**
     * Translates a batch of numeric user ids into screen names,
     * by looking them up via the Twitter API.  The list may not
     * be longer than MAX_LOOKUPS users (currently 100).
     * @param ids the user ids to be looked up
     * @return a map from user ids to the corresponding screen names
     */
    public Map<Integer,String> lookupIds (int[] ids) {
        if (ids == null) throw new IllegalArgumentException();
        if (ids.length == 0) return new HashMap<Integer,String>();
        if (ids.length > MAX_LOOKUPS) {
            throw new IllegalArgumentException(
                "only " + MAX_LOOKUPS + " ids can be resolved at a time "
              + "(parameter array contains " + ids.length + ")"
            );
        }
        OAuthRequest request = new OAuthRequest(
            Verb.POST, "https://api.twitter.com/1.1/users/lookup.json"
        );
        StringBuilder idList = new StringBuilder();
        for (int i=0; i<ids.length; i++) {
            idList.append(ids[i]);
            if (i<ids.length-1) idList.append(",");
        }
        request.addBodyParameter("user_id", idList.toString());
        oauthService.signRequest(accessToken, request);
        Response response = request.send();
        if (!response.isSuccessful()) throw TwitterException.create(response);
        BasicDBList result = (BasicDBList)JSON.parse(response.getBody());
        Map<Integer,String> m = new HashMap<Integer,String>();
        for (Object o : result) {
            DBObject dbo = (DBObject)o;
            int id = (Integer)dbo.get("id");
            String name = (String)dbo.get("screen_name");
            m.put(id, name);
        }
        if (m.size() == 0) throw new TwitterException("empty response");
        return m;
    }

    /**
     * Returns the screen name for the given user id, by making
     * a single request to the Twitter API.
     * @param id the user id to lookup
     * @return the screen name of the user
     * @throws PageNotExistException if there is no user with the given id
     */
    public String getScreenName (int id) {
        DBObject result = request("users/lookup.json?user_id="+id);
        return (String)((DBObject)((BasicDBList)result).get(0)).get("screen_name");
    }
    
    /**
     * Returns the user id for the given screen name, by making
     * a single request to the Twitter API.
     * @param screenName the screen name to look up
     * @return the id of the user
     * @throws PageNotExistException if there is no user with the given screen name
     */
    public int getId (String screenName) {
        DBObject result = request("users/lookup.json?screen_name="+screenName);
        return (Integer)((DBObject)((BasicDBList)result).get(0)).get("id");
    }
    
    /**
     * Returns the numeric user ids of all followers of the given user.
     * This method makes repeated calls to the Twitter API until all
     * followers have been retrieved (each call returns up to 5000
     * followers). In order not to run against the rate
     * limit, a maximum of MAX_FOLLOWER_BATCHES calls are made. If the
     * user has more followers than can be retrieved that way,
     * a TwitterException is thrown.
     * @param screenName of the user for which followers should be retrieved
     * @return the list of the user's followers
     * @throws TwitterException if the user has more followers than can be
     * retrieved by repeated calls to the API
     * @throws PageNotExistException if there is no user with the given
     * screen name
     */
    public List<Integer> getFollowers (String screenName) {
        int id = getId(screenName);
        return getFollowers(id, -1, MAX_FOLLOWER_BATCHES);
    }
    
    /**
     * Returns the numeric user ids of all followers of the given user.
     * This method makes repeated calls to the Twitter API until all
     * followers have been retrieved (each call returns up to 5000
     * followers). In order not to run against the rate
     * limit, a maximum of MAX_FOLLOWER_BATCHES calls are made. If the
     * user has more followers than can be retrieved that way,
     * a TwitterException is thrown.
     * @param id the id of the user whose followers should be retrieved
     * @return the list of the user's followers
     * @throws TwitterException if the user has more followers than can be
     * retrieved by repeated calls to the API
     * @throws PageNotExistException if there is no user with the given id
     */
    public List<Integer> getFollowers (int id) {
        return getFollowers(id, -1, MAX_FOLLOWER_BATCHES);
    }
    
    /**
     * Returns one batch of followers (up to 5000), starting from the
     * given cursor (which was retrieved in a previous call to this method).
     * @param id the id of the user whose followers should be retrieved
     * @param cursor the cursor from which to start returning followers, or -1
     * if this is the first batch
     * @param result returned follower ids are added to this list
     * @return the next cursor, if more followers can be retrieved,
     * or -1 if the follower list has been exhausted
     */
    public long getFollowerBatch (int id, long cursor, List<Integer> result) {
        DBObject response = request("followers/ids.json?user_id="+id+"&cursor="+cursor);
        BasicDBList list = (BasicDBList)response.get("ids"); 
        for (Object o : list) {
            int i = (Integer)o;
            result.add(i);
        }
        long next_cursor = Long.parseLong((String)response.get("next_cursor_str"));
        return next_cursor;
    }
    
    private List<Integer> getFollowers (int id, long cursor, int numBatchesAllowed) {
        List<Integer> result = new ArrayList<Integer>();
        long next_cursor = getFollowerBatch (id, cursor, result);
        if (next_cursor != 0) {
            if (numBatchesAllowed > 1) {
                result.addAll (getFollowers (id, next_cursor, numBatchesAllowed-1));
            } else {
                throw new TwitterException ("maximum number of batches exceeded");
            }
        }
        return result;
    }
    
    /**
     * Returns the single instance of this class.
     */
    public static Twitter getInstance() {
        if (instance == null) {
            Properties conf = new Properties();
            try {
                conf.load(Twitter.class.getResourceAsStream("/twitter.properties"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            instance = new Twitter (conf.getProperty("api_key"),
                                    conf.getProperty("api_secret"),
                                    conf.getProperty("access_token"),
                                    conf.getProperty("access_secret"));
        }
        return instance;
    }
    
    public static void main(String[] args) {
        // List<Integer> result = getInstance().getFollowers("MongoDB");
        // System.out.println("follower count: " + result.size());
        System.out.println(getInstance().getId("drmirror"));
    }
    
    
    
}
