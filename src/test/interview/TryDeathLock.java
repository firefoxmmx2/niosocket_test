package test.interview;

/**
 * 编写一个程序使得两个线程陷入死锁
 */
public class TryDeathLock {
  private Integer resource1 = 0;
  private Integer resource2 = 0;

  public TryDeathLock() {
    new IncreaseResource();
    new ReduceResource();
  }

  class IncreaseResource extends Thread {
    public IncreaseResource() {
      this.start();
    }

    @Override
    public void run() {
      while(true){
        synchronized (resource1){
          synchronized (resource2) {
            resource1 += 1;
            resource2 += 1;
          }
        }
      }
    }
  }

  class ReduceResource extends Thread {
    @Override
    public void run() {
      while(true){
        synchronized (resource2) {
          synchronized (resource1) {
            resource1 -= 1;
            resource2 -= 2;
          }
        }
      }
    }

    public ReduceResource() {
      this.start();
    }
  }

  public static void main(String[] args) throws InterruptedException {
    TryDeathLock lock = new TryDeathLock();

    for (int i = 0; i < 1000000; i++) {
      System.out.println(lock.resource1);
      System.out.println(lock.resource2);
    }
  }
}


