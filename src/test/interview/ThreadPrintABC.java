package test.interview;

public class ThreadPrintABC {
  public void sleepMethod() throws InterruptedException {
    final int limit = 10;
    class C implements Runnable {
      @Override
      public void run() {
        for (int i = 0; i < limit; i++) {
          System.out.println("C");
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }

    class B implements Runnable {
      @Override
      public void run() {
        for (int i = 0; i < limit; i++) {
          System.out.print("B");
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }

    class A implements Runnable {
      @Override
      public void run() {
        for (int i = 0; i < 10; i++) {
          System.out.print("A");
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }


    new Thread(new A()).start();
    Thread.sleep(10);
    new Thread(new B()).start();
    Thread.sleep(10);
    new Thread(new C()).start();
    Thread.sleep(10);
  }

  public void synchronizedMethod() throws InterruptedException {
    final Object object=new Object();
    final int limit=10;
    class A extends Thread {
      @Override
      public void run() {
        for (int i = 0; i < limit; i++) {
          synchronized (object){
            System.out.print("A");
            if(i<limit-1) {
              try {
                object.wait();
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
          }
        }
      }

      public A() {
        this.start();
      }
    }

    class B extends Thread {

      @Override
      public void run() {
        for (int i = 0; i < limit; i++) {
          synchronized (object) {
            System.out.print("B");
            if(i<limit-1) {
              try {
                object.wait();
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
          }
        }
      }

      public B() {
        this.start();
      }
    }

    class C extends Thread {
      public C() {
        this.start();
      }

      @Override
      public void run() {
        for (int i = 0; i < limit; i++) {
          synchronized (object){
            object.notifyAll();
            System.out.println("C");
            if(i<limit-1) {
              try {
                object.wait();
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
          }
        }
      }
    }
    A a = new A();
    B b = new B();
    C c = new C();
  }
  public static void main(String[] args) throws InterruptedException {
    ThreadPrintABC abc = new ThreadPrintABC();
//    abc.sleepMethod();
//    Thread.sleep(1000);
    abc.synchronizedMethod();
  }
}
