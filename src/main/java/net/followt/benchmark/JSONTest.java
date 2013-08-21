package net.followt.benchmark;

import com.mongodb.util.JSON;

public class JSONTest {

    public static void main(String[] args) {
        Object result = JSON.parse("'blabla'");
        System.out.println(result + " " + result.getClass());
    }

}
