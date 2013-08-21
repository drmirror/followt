package net.followt;

import java.util.Map;

import junit.framework.TestCase;

public class TwitterTest extends TestCase {

    Twitter twitter = Twitter.getInstance();
    
    public void test_getId_drmirror() {
        int id = twitter.getId("drmirror");
        assertEquals(22603349, id);
    }
    
    public void test_getId_empty() {
        try {
            twitter.getId("");
        } catch (Exception ex) {
            // ok
        }
    }

    public void test_getId_null() {
        try {
            twitter.getId(null);
        } catch (Exception ex) {
            // ok
        }
    }
    
    public void test_getScreenName_drmirror() {
        String name = twitter.getScreenName(22603349);
        assertEquals("drmirror", name);
    }
    
    public void test_lookupIds_1() {
        int[] ids = new int[] {22603349, 15346486, 134528249, 97022731};
        Map<Integer,String> result = twitter.lookupIds(ids);
        assertEquals(4, result.size());
        assertEquals("drmirror",     result.get(22603349));
        assertEquals("maennig",      result.get(15346486));
        assertEquals("stattkatze",   result.get(134528249));
        assertEquals("infinsternis", result.get(97022731));
    }

    public void test_lookupIds_empty() {
        int[] ids = new int[] {};
        Map<Integer,String> result = twitter.lookupIds(ids);
        assertEquals(0, result.size());
    }

}
