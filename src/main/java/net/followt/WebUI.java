package net.followt;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.followt.FollowT.Follower;
import net.followt.FollowT.Unfollower;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import spark.template.freemarker.FreeMarkerRoute;
import freemarker.template.SimpleHash;
import freemarker.template.TemplateModel;

/**
 * The web-based user interface of the followt application,
 * implemented using Spark and Freemarker.
 * @author drmirror
 */
public class WebUI {

    private static FollowT ui = FollowT.getInstance();
    
    public static void main(String[] args) {
        Spark.staticFileLocation("/spark/static");
        
        Spark.get(new FreeMarkerRoute("/") {
            @Override
            public Object handle(Request request, Response response) {
                SimpleHash root = new SimpleHash();
                root.put("interval",86400000);
                root.put("followers",Collections.EMPTY_LIST);
                root.put("unfollowers",Collections.EMPTY_LIST);
                return modelAndView(root,"report.ftl");
            }
        });
        Spark.get(new FreeMarkerRoute("/report/:screen_name") {
            @Override
            public ModelAndView handle(Request request, Response response) {
                String screen_name = request.params("screen_name");
                String is = request.queryParams("interval");
                if (is == null) is = "86400000";
                long interval = Long.parseLong(is);
                        
                TemplateModel root = getModel (screen_name, interval);
                return modelAndView(root,"report.ftl");
            }
        });
        Spark.post(new Route("/report") {
            @Override
            public Object handle(Request request, Response response) {
                String screen_name = request.queryParams("screen_name");
                String interval = request.queryParams("interval");
                if (interval == null) interval = "86400000";
                String url = "/report/" + screen_name + "?interval=" + interval;
                response.redirect(url);
                return null;
            }
        });
    }
    
    private static TemplateModel getModel (String screen_name, long interval) {
        List<Follower> followers = ui.getRecentFollowers(screen_name, interval);
        List<Unfollower> unfollowers = ui.getRecentUnfollowers(screen_name, interval);
        int unfollowers_deleted = 0;
        for (Iterator<Unfollower> i = unfollowers.iterator(); i.hasNext();) {
            Unfollower u = i.next();
            if (u.getFollowerScreenName().startsWith("*")) {
                i.remove();
                unfollowers_deleted++;
            }
        }
        SimpleHash root = new SimpleHash();
        root.put("screen_name", screen_name);
        root.put("interval", interval);
        root.put("followers", followers);
        root.put("unfollowers", unfollowers);
        root.put("unfollowers_deleted", unfollowers_deleted);
        return root;
    }
    
}
