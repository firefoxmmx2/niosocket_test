package test.buildpatch;

import test.common.StringUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 一个简单的临时的补丁打包工具
 */
public class PatchMaker {
    public static final boolean isDebug = true;
    /**
     * 部署在服务器上面的地址
     */
    public static final String PROJECT_ARTIFACT_DIR = "/home/hooxin/Work/加油站散装油系统/out/artifacts/_war_exploded";
    /**
     * class文件夹名称
     */
    public static final String CLASSES_DIR = "/WEB-INF/classes";
    /**
     * class文件夹的真实路径
     */
    public static final String PROJECT_ARTIFACT_CLASSES_DIR = PROJECT_ARTIFACT_DIR + CLASSES_DIR;
    /**
     * 补丁输出文件夹
     */
    public static final String PATCH_OUT_DIR = "/home/hooxin/Work/加油站散装油系统/补丁/加油站散装油系统v1.7";
    /**
     * 改动补丁文件夹列表(可以直接使用idea格式)
     */
    public static final String INPUT_FILE = "/home/hooxin/加油站散装油文件更新列表.txt";

    /**
     * 打包补丁
     */
    public void buildPatch() throws IOException {
        File patchOutDir = new File(PATCH_OUT_DIR);
        if (!patchOutDir.exists()) {
            patchOutDir.mkdir();
        }
        File inputFile = new File(INPUT_FILE);
        BufferedReader bufferedReader = new BufferedReader(new FileReader(inputFile));
        String filepath = "";
        while ((filepath = bufferedReader.readLine()) != null) {
            String fromPath = "";
            String toPath = "";
            if (filepath.contains("."))
                if (filepath.contains("src/")) {
                    String path = filepath.substring(filepath.indexOf("src/") + "src/".length())
                            .replaceAll("\\.java", ".class");
                    fromPath = PROJECT_ARTIFACT_CLASSES_DIR + "/" + path;
                    toPath = PATCH_OUT_DIR + "/" + "程序" + CLASSES_DIR + "/" + path;
                } else if (filepath.contains("WebRoot/")) {
                    String path = filepath.substring(filepath.indexOf("WebRoot/") + "WebRoot/".length());
                    fromPath = PROJECT_ARTIFACT_DIR + "/" + path;
                    toPath = PATCH_OUT_DIR + "/" + "程序" + "/" + path;
                }
            if (StringUtils.isNotEmpty(fromPath) && StringUtils.isNotEmpty(toPath)) {
                File patchFilePath = new File(toPath);
                if(!patchFilePath.getParentFile().exists())
                    patchFilePath.getParentFile().mkdirs();
                FileInputStream in=new FileInputStream(fromPath);
                FileOutputStream out=new FileOutputStream(patchFilePath);
                FileChannel inChannel=in.getChannel();
                FileChannel outChannel=out.getChannel();
                ByteBuffer buffer=ByteBuffer.allocate(1024*2);
                while(inChannel.read(buffer)>0){
                    buffer.flip();
                    outChannel.write(buffer);
                    buffer.clear();
                }

                outChannel.close();
                inChannel.close();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        PatchMaker pm = new PatchMaker();
        pm.buildPatch();
    }
}
