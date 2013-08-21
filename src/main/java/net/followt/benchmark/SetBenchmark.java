package net.followt.benchmark;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;

public abstract class SetBenchmark {

    public final static int VALUE_RANGE = 1000000;
    public final static int SET_SIZE    = 100000;
    public final static int DIFF_SIZE   = 100;
    
    public static int FOLLOWEE = 123456;
    public static DB db = null;
    
    public DBCollection getCollection (String name) {
        if (db == null) {
            MongoClient c;
            try {
                c = new MongoClient();
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            db = c.getDB("ftest");
        }
        return db.getCollection(name);
    }
    
    public void createTestData (List<Integer> a, List<Integer> b, List<Integer> c) {
        // create value array
        List<Integer> values = new ArrayList<Integer>(VALUE_RANGE);
        for (int i=0; i<VALUE_RANGE; i++) {
            values.add(i);
        }
        a.clear();
        b.clear();
        c.clear();
        // insert SET_SIZE random values into a
        for (int i=0; i<SET_SIZE; i++) {
            if (i%1000==0) System.out.println(i);
            int index = (int)(Math.random() * values.size());
            int value = values.get(index);
            values.remove(index);
            a.add(value);
        }
        b.addAll(a);
        // remove DIFF_SIZE random values from b and add them to c
        for (int i=0; i<DIFF_SIZE; i++) {
            int index = (int)(Math.random() * b.size());
            int value = b.get(index);
            b.remove(index);
            c.add(value);
        }
    }
    
    protected DBCollection fhistory = null;
    protected DBCollection fcurrent = null;
    
    public void prepare (List<Integer> a, List<Integer> b, List<Integer> c) {
        Date now = new Date();
        fhistory = getCollection("fhistory");
        fhistory.drop();
        fhistory.ensureIndex(new BasicDBObject("followee",1)
                                       .append("follower",1));
        for (int x : a) {
            fhistory.insert(new BasicDBObject("followee",FOLLOWEE)
                                      .append("follower",x)
                                      .append("start", now)
                                      .append("last", now));
        }
    }
    
    public void prepareCurrent(List<Integer> a, List<Integer> b, List<Integer> c) {
        fcurrent = getCollection("fcurrent");
        fcurrent.drop();
        fcurrent.ensureIndex(new BasicDBObject("followee",1).append("follower",1));
        for (int x: b) {
            fcurrent.insert(new BasicDBObject("followee",FOLLOWEE)
                                      .append("follower",x));
        }
    }
    
    public long doBenchmark() {
        List<Integer> a = new ArrayList<Integer>();
        List<Integer> b = new ArrayList<Integer>();
        List<Integer> c = new ArrayList<Integer>();

        System.out.print("creating test data... ");
        createTestData(a,b,c);
        System.out.println("done");

        System.out.print("preparing... ");
        prepare(a,b,c);
        System.out.println("done");

        System.out.print("measuring... ");
        long result = measure(a,b,c);
        System.out.print("done... ");

        return result;
    }
    
    public abstract void computeDifference (List<Integer> a, List<Integer> b, List<Integer> c);
    
    public long measure (List<Integer> a, List<Integer> b, List<Integer> c) {
        long startTime = System.currentTimeMillis();
        computeDifference(a,b,c);
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }
    
    public static void main(String[] args) {
        SetBenchmark s = new MultiSearchDatabase();
        long time = s.doBenchmark();
        System.out.println(time + " ms");
    }

}
