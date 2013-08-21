package net.followt.benchmark;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class MultiSearchMemory extends SetBenchmark {

    public void computeDifference(List<Integer> a, List<Integer> b, List<Integer> c) {
        DBCursor history = fhistory.find(new BasicDBObject("followee", FOLLOWEE));
        Set<Integer> s = new HashSet<Integer>(b);
        int count = 0;
        for (DBObject o : history) {
            int follower = (Integer)o.get("follower");
            if (!s.contains(follower)) {
                fhistory.update(new BasicDBObject("followee", FOLLOWEE)
                                          .append("follower", follower),
                                new BasicDBObject("$set", new BasicDBObject("end", new Date())));
                count++;
            }
        }
        if (count != DIFF_SIZE) throw new RuntimeException ("wrong result: " + count);
    }

}
