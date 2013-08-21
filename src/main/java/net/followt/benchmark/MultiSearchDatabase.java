package net.followt.benchmark;

import java.util.Date;
import java.util.List;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class MultiSearchDatabase extends SetBenchmark {

    public void prepare (List<Integer> a, List<Integer> b, List<Integer> c) {
        super.prepare(a,b,c);
        prepareCurrent(a,b,c);
    }
    
    public void computeDifference(List<Integer> a, List<Integer> b, List<Integer> c) {
        DBCursor history = fhistory.find(new BasicDBObject("followee", FOLLOWEE));
        int count = 0;
        for (DBObject o : history) {
            int follower = (Integer)o.get("follower");
            DBObject result = fcurrent.findOne(new BasicDBObject("followee", FOLLOWEE)
                                                         .append("follower", follower));
            if (result == null) {
                fhistory.update(new BasicDBObject("followee", FOLLOWEE)
                                          .append("follower", follower),
                                new BasicDBObject("$set", new BasicDBObject("end", new Date())));
                count++;
            }
            
        }
        if (count != DIFF_SIZE) throw new RuntimeException ("wrong result: " + count);
    }

}
