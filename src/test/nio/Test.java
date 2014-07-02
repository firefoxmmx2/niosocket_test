package test.nio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by hooxin on 14-7-2.
 */
public class Test {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        bos.write("hellworld".getBytes());
        System.out.println("bos.size()="+bos.size());
        bos.reset();
        System.out.println("after bos.reset(), bos.size()="+bos.size());
    }
}
