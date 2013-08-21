package net.followt.benchmark;

import java.util.List;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class MergeDatabase extends SetBenchmark {

    public void prepare (List<Integer> a, List<Integer> b, List<Integer> c) {
        super.prepare(a,b,c);
        prepareCurrent(a,b,c);
    }
    
    public void computeDifference(List<Integer> a, List<Integer> b, List<Integer> c) {
        int count = 0;
        DBCursor history = fhistory.find(new BasicDBObject("followee", FOLLOWEE))
                                   .sort(new BasicDBObject("follower",1));
        DBCursor current = fcurrent.find(new BasicDBObject("followee", FOLLOWEE))
                                   .sort(new BasicDBObject("follower",1));
        // assuming that fcurrent is a true subset of fhistory
        while (true) {
            if (!history.hasNext()) break;
            if (!current.hasNext()) {
                count++;
                break;
            }
            DBObject his = history.next();
            DBObject cur = current.next();
            while(!his.get("follower").equals(cur.get("follower"))) {
                count++;
                his = history.next();
            }
        }
        if (count != DIFF_SIZE) throw new RuntimeException ("wrong result: " + count);
    }

}
