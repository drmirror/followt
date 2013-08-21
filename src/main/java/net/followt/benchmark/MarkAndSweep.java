package net.followt.benchmark;

import java.util.Date;
import java.util.List;

import com.mongodb.BasicDBObject;

public class MarkAndSweep extends SetBenchmark {

    Date scanTime = null;
    
    public void prepare (List<Integer> a, List<Integer> b, List<Integer> c) {
        super.prepare(a,b,c);
        scanTime = new Date();
        for (int x : b) {
            fhistory.update(new BasicDBObject("followee", FOLLOWEE)
                                      .append("follower", x),
                            new BasicDBObject("$set", new BasicDBObject ("last", scanTime)));          
        }
    }
    
    public void computeDifference(List<Integer> a, List<Integer> b, List<Integer> c) {
        Date now = new Date();
        fhistory.update(new BasicDBObject("followee", FOLLOWEE)
                                  .append("last", new BasicDBObject("$lt", scanTime)),
                        new BasicDBObject("$set", new BasicDBObject("end", now)),
                        false, true);
    }

}
