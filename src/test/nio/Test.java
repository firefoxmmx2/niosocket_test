package test.nio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by hooxin on 14-7-2.
 */
public class Test {
    public static void main(String[] args) throws IOException {
        Test t=new Test();
        t.staticClassProperties();
    }

    public void testByteArrayOutputSteam() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write("hellworld".getBytes());
        System.out.println("bos.size()=" + bos.size());
        bos.reset();
        System.out.println("after bos.reset(), bos.size()=" + bos.size());
    }

    public void staticClassProperties() {
        TestP p = new TestP();
        System.out.println("new TestP() = " + p);
        System.out.println("new TestP().s = " + p.s);
        p = new TestP();
        new TestP().s = p.s + "123";
        System.out.println("new TestP() = "+p);
        System.out.println("new TestP().s + 123 = " + new TestP().s);
    }


    static class TestP {
        static String s = "hello world";
    }
}
