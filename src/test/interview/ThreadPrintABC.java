package test.interview;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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

  public void synchronizedWaitNotifyMethod() throws InterruptedException {
    class Controller {
      private String currentWord;

      public void nextWord() {
        if("A".equals(currentWord)){
          currentWord="B";
        }
        else if("B".equals(currentWord)){
          currentWord="C";
        }
        else if("C".equals(currentWord)) {
          currentWord="A";
        }
        else {
          currentWord="A";
        }
      }

      public String getCurrentWord() {
        return currentWord;
      }
    }

    final int limit=10;
    ExecutorService executorService = Executors.newFixedThreadPool(3);
    final Controller controller=new Controller();

    class PrintABC implements Runnable {
      private final String word;

      public PrintABC(String word) {
        this.word = word;
      }

      @Override
      public void run() {
        for (int i = 0; i < limit; i++) {
          synchronized (controller){
            while(!controller.getCurrentWord().equals(word))  {
              try {
                controller.wait();
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
            if("C".equals(word)) {
              System.out.println(word);
            }
            else {
              System.out.print(word);
            }
            controller.notifyAll();
            controller.nextWord();
          }

        }
      }
    }

    controller.nextWord();
    executorService.execute(new PrintABC("A"));
    executorService.execute(new PrintABC("B"));
    executorService.execute(new PrintABC("C"));

    executorService.shutdown();

  }

  public void reentrantLockConditionMethod() {
    final int limit=10;
    ExecutorService executorService=Executors.newFixedThreadPool(3);
    ReentrantLock lock=new ReentrantLock();
    final Condition condition=lock.newCondition();

    class Controller {
      private String word="A";

      public void nextWord() {
        if("A".equals(word)){
          word="B";
        }
        else if("B".equals(word)){
          word="C";
        }
        else if("C".equals(word)) {
          word="A";
        }
      }

      public String getWord() {
        return word;
      }

      public void printABC() {
        System.out.print(word);
        if("C".equals(word))
          System.out.println();
      }
    }
    class PrintABC implements Runnable {
      private String word;
      private ReentrantLock lock;
      private Controller controller;
      private Condition condition;

      public PrintABC(String word, ReentrantLock lock, Controller controller, Condition condition) {
        this.word = word;
        this.lock = lock;
        this.controller = controller;
        this.condition = condition;
      }

      @Override
      public void run() {
        for (int i = 0; i < limit; i++) {
          lock.lock();
          try {
            while(!word.equals(controller.getWord())){
              try {
                condition.await();
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
            controller.printABC();
            controller.nextWord();
            condition.signalAll();
          } finally {
            lock.unlock();
          }

        }
      }
    }
    Controller controller=new Controller();
    executorService.execute(new PrintABC("A",lock,controller,condition));
    executorService.execute(new PrintABC("B",lock,controller,condition));
    executorService.execute(new PrintABC("C",lock,controller,condition));
    executorService.shutdown();
  }
  public static void main(String[] args) throws InterruptedException {
    ThreadPrintABC abc = new ThreadPrintABC();
//    abc.sleepMethod();
//    Thread.sleep(1000);
//    abc.synchronizedWaitNotifyMethod();
    abc.reentrantLockConditionMethod();
  }
}
