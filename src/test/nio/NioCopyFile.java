package test.nio;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by hooxin on 14-6-19.
 */
public class NioCopyFile {
    public static void main(String[] args) {
        String inFilename = "/home/hooxin/保安服装语句.txt";
        String outFilename = "/home/hooxin/测试.txt";
        FileChannel inFileChannel = null;
        FileChannel outFileChannel = null;
        try {
            inFileChannel = new FileInputStream(inFilename).getChannel();
            outFileChannel = new FileOutputStream(outFilename).getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int len = 0;
            while ((len = inFileChannel.read(buffer)) != -1) {
                buffer.flip();
                outFileChannel.write(buffer);
                buffer.clear();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                inFileChannel.close();
                outFileChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }


    }
}
