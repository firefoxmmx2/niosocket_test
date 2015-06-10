package test.buildpatch;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImportDataFromXml {
  //注意在执行前做好xml文件的备份,当一个xml被成功执行以后会被清楚.
  private String dirname = "/home/hooxin/Downloads/xmls"; // 扫描文件夹路径
  private String dbUsername = "basys";
  private String dbPassword = "basys";
  private String dbUrl = "jdbc:oracle:thin:@192.168.1.202:1521:test";
  private String slsqjbxxidIncludePath; // SLSQJBXXID 满足的数字列表;
  private Connection connection;

  public ImportDataFromXml() throws ClassNotFoundException {
    Class.forName("oracle.jdbc.driver.OracleDriver");
  }

  public void importImageData(String sql) throws IOException, SQLException {
    sql = sql.substring(sql.indexOf("image:")+"image:".length());
    String regexp = "DZCLNR='([a-zA-z/+-_0-9]*)'";
    Pattern compile = Pattern.compile(regexp);
    Matcher matcher = compile.matcher(sql);
    if(matcher.find()){
      String contentBase64 = matcher.group(1);
      byte[] content=null;
      if(!"".equals(contentBase64)){
        sun.misc.BASE64Decoder base64Decoder = new sun.misc.BASE64Decoder();
        content = base64Decoder.decodeBuffer(contentBase64);
      }
      sql=sql.replaceAll(regexp,"DZCLNR=?");
      PreparedStatement statement = connection.prepareStatement(sql);
      try {
        System.out.println(sql);
        statement.setBytes(1,content);
        statement.execute();

      }
      finally {
        if(statement!=null)
          statement.close();
      }
    }
  }

  public void insert(Document document) throws SQLException {
    Element root = document.getRootElement();
    //找到所有满足 BASYS.T_BAGSDZCLTJ 并且类型为 INSERT 的记录
    List<Element> insertRecords = root.selectNodes("//Package/Record[TABLENAME=\"BASYS.T_BAGSDZCLTJ\"][SQLTYPE=\"INSERT\"]");
    for (Element record : insertRecords) {
      //sql 像这样的 insert into BASYS.T_BAGSDZCLTJ(BAGSDZCLTJID,DZCLLX,DZCLLXDM,DZCLMC,DZCLNR,DZCLWJLX,QYBGBZ,SLSQJBXXID) values (500000066,null ,'slsqsdzcl','设立申请书.doc',null,'.doc',null ,2382)
      String sql=record.element("SQLEXE").getText();
      if(sql==null || sql.length()==0){
        continue;
      }
      Statement statement= null;
      try {
        System.out.println(sql);
        statement = connection.createStatement();
        statement.execute(sql);
      }
      finally {
        if(statement!=null)
          statement.close();
      }
    }
  }

  public void delete(Document document) throws SQLException {
    Element root = document.getRootElement();
    //找到所有满足 BASYS.T_BAGSDZCLTJ 并且类型为 DELETE 的记录
    List<Element> deleteRecords = root.selectNodes("//Package/Record[TABLENAME=\"BASYS.T_BAGSDZCLTJ\"][SQLTYPE=\"DELETE\"]");
    for (Element record : deleteRecords) {
      String sql = record.element("SQLEXE").getText();
      if(sql==null || sql.length()==0){
        continue;
      }
      Statement statement= null;
      try {
        System.out.println(sql);
        statement = connection.createStatement();
        statement.execute(sql);
      }
      finally {
        if(statement!=null)
          statement.close();
      }
    }
  }

  public void update(Document document) throws SQLException, IOException {
    Element root = document.getRootElement();
    //找到所有满足BASYS.T_BAGSDZCLTJ 类型为 UPDATE 的记录
    List<Element> updateRecords = root.selectNodes("//Package/Record[TABLENAME=\"BASYS.T_BAGSDZCLTJ\"][SQLTYPE=\"UPDATE\"]");
    for (Element record : updateRecords) {
      String sql = record.element("SQLEXE").getText();
      if(sql==null || sql.length()==0){
        continue;
      }
      if(sql.indexOf("image:") != -1){
        // 为附件或者图片类型的语句
        importImageData(sql);
      }
      else{
        Statement statement= null;
        try {
          System.out.println(sql);
        statement = connection.createStatement();
        statement.execute(sql);
        }
        finally {
          if(statement!=null)
            statement.close();
        }
      }
    }
  }

  public void build() throws SQLException {
    connection = DriverManager.getConnection(dbUrl,dbUsername,dbPassword);

    try {
      File scanDir = new File(dirname);
      FileFilter fileFilter = new FileFilter() {
        public boolean accept(File pathname) {
          if (pathname.getName().contains(".xml"))
            return true;
          else
            return false;
        }
      };
      SAXReader reader = new SAXReader();
      if (scanDir.isDirectory()) {
        File[] files = scanDir.listFiles(fileFilter);
        for (File xml : files) {
          Document document=null;
          try {
            document = reader.read(xml);
          } catch (DocumentException e) {
            System.err.println(xml.getName() + "文件不是一个有效的xml");
            continue;
          }
          importData(document);
          //在操作完成以后,删除这个已经处理过的xml
          xml.delete();
        }
      }
      else {
        Document document = null;
        try {
          document = reader.read(scanDir);
          importData(document);
        } catch (DocumentException e) {
          System.err.println(scanDir.getName() + "文件不是一个有效的xml");
        }

      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      connection.close();
    }

  }

  public void importData(Document document){

    try {
      //插入数据
      insert(document);
      //删除数据
      delete(document);
      //更新数据
      update(document);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  public static void main(String[] args) throws SQLException, ClassNotFoundException {
    ImportDataFromXml importDataFromXml = new ImportDataFromXml();
    importDataFromXml.build();
  }
}
