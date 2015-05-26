package test.buildpatch;

import test.common.StringUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * 一个简单的临时的补丁打包工具
 */
public class PatchMaker {
    private boolean isDebug = true;

    private String PROJECT_ROOT = "/home/hooxin/Work/加油站散装油系统";
    /**
     * 部署在服务器上面的地址
     */
    private String PROJECT_ARTIFACT_DIR = PROJECT_ROOT + "/out/artifacts/_war_exploded";
    /**
     * class文件夹名称
     */
    private String CLASSES_DIR = "/WEB-INF/classes";
    /**
     * class文件夹的真实路径
     */
    private String PROJECT_ARTIFACT_CLASSES_DIR = PROJECT_ARTIFACT_DIR + CLASSES_DIR;
    /**
     * 补丁输出文件夹
     */
    private String PATCH_OUT_DIR = "/home/hooxin/Work/加油站散装油系统/补丁/加油站散装油系统v2.3.2-v2.3.3";
    /**
     * 改动补丁文件夹列表(可以直接使用idea格式)
     */
    private String INPUT_FILE = "/home/hooxin/加油站散装油文件更新列表.txt";
    /**
     * 源码目录
     */
    private String SOURCE_DIR = "源码";
    /**
     * java源码目录
     */
    private String JAVA_SOURCE_DIR = "src";
    /**
     * 数据库脚本目录
     */
    private String DATABASE_SCRIPT_DIR = "数据库脚本";
    /**
     * 网页根目录
     */
    private String WEB_ROOT = "WebRoot";

    private List<String> EXCLUDE_WORDS=new ArrayList<String>();

    //初始化
    public PatchMaker(){
        //默认添加补丁为排除关键字
        EXCLUDE_WORDS.add("补丁");
    }
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
            List<String> fromPaths = new ArrayList<String>();
            List<String> toPaths = new ArrayList<String>();
            File file = new File(filepath);
            boolean hasExcludeWords=false;
            for (String excludeWord : EXCLUDE_WORDS) {
                if(filepath.contains(excludeWord)){
                    hasExcludeWords=true;
                    break;
                }
            }
            if (file.getName().contains(".") && !hasExcludeWords)
                if (filepath.contains(SOURCE_DIR + "/") && filepath.contains(JAVA_SOURCE_DIR + "/")) {        //源码
                    String path = filepath.substring(filepath.indexOf(JAVA_SOURCE_DIR + "/") + (JAVA_SOURCE_DIR + "/").length())
                            .replaceAll("\\.java", ".class");
                    fromPaths.add(PROJECT_ARTIFACT_CLASSES_DIR + "/" + path);
                    toPaths.add(PATCH_OUT_DIR + "/" + "程序" + CLASSES_DIR + "/" + path);

                    if (path.contains(".class")) {   //支持内部类
                        final File fromPathFile0 = new File(PROJECT_ARTIFACT_CLASSES_DIR + "/" + path);
                        File[] subclassFiles = fromPathFile0.getParentFile().listFiles(new FilenameFilter() {
                            @Override
                            public boolean accept(File dir, String name) {
                                if (name.contains(fromPathFile0.getName().split(".class")[0] + "$"))
                                    return true;
                                else
                                    return false;
                            }
                        });
                        if (subclassFiles != null)
                            for (int i = 0; i < subclassFiles.length; i++) {
                                String subclass = subclassFiles[i].getPath();
                                fromPaths.add(subclass);
                                toPaths.add(PATCH_OUT_DIR + "/" + "程序" + CLASSES_DIR + "/" + subclass.split(PROJECT_ARTIFACT_CLASSES_DIR + "/")[1]);
                            }
                    }

                } else if (filepath.contains(SOURCE_DIR + "/") && filepath.contains(WEB_ROOT + "/")) { //WEB根目录
                    String path = filepath.substring(filepath.indexOf(WEB_ROOT + "/") + (WEB_ROOT + "/").length());
                    fromPaths.add(PROJECT_ARTIFACT_DIR + "/" + path);
                    toPaths.add(PATCH_OUT_DIR + "/" + "程序" + "/" + path);
                } else if (filepath.contains(DATABASE_SCRIPT_DIR + "/")) {   //数据库脚本
                    String path = filepath.substring(filepath.indexOf(DATABASE_SCRIPT_DIR + "/")
                            + (DATABASE_SCRIPT_DIR + "/").length());
                    fromPaths.add(PROJECT_ROOT + "/" + DATABASE_SCRIPT_DIR + "/" + path);
                    toPaths.add(PATCH_OUT_DIR + "/" + DATABASE_SCRIPT_DIR + "/" + path);
                }

            for (int i = 0; i < fromPaths.size(); i++) {
                String fromPath = fromPaths.get(i);
                String toPath = toPaths.get(i);
                writeFile(fromPath, toPath);
            }
        }
    }

    private void writeFile(String fromPath, String toPath) throws IOException {
        if (StringUtils.isNotEmpty(fromPath) && StringUtils.isNotEmpty(toPath)) {
            File patchFilePath = new File(toPath);
            if (!patchFilePath.getParentFile().exists())
                patchFilePath.getParentFile().mkdirs();
            FileInputStream in = new FileInputStream(fromPath);
            FileOutputStream out = new FileOutputStream(patchFilePath);
            FileChannel inChannel = in.getChannel();
            FileChannel outChannel = out.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 2);
            while (inChannel.read(buffer) > 0) {
                buffer.flip();
                outChannel.write(buffer);
                buffer.clear();
            }

            outChannel.close();
            inChannel.close();
        }
    }

    public static void main(String[] args) throws IOException {
        PatchMaker pm = new PatchMaker();
        pm.buildPatch();
    }
}
