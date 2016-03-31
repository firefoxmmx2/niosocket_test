package test.exception;

import java.util.logging.Logger;

public class SimpleExceptionTest {
    Logger log=Logger.getLogger(SimpleExceptionTest.class.getName());
    private String firstName;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public static void main(String[] args) {
       SimpleExceptionTest simpleExceptionTest=new SimpleExceptionTest();

        try {
            System.out.println(simpleExceptionTest.getFirstName().equals("1"));
        } catch (Exception e) {
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                System.out.println(stackTraceElement);
            }
            System.out.println(e.getStackTrace());
        }
    }
}
