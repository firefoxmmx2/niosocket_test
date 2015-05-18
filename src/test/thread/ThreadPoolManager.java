package test.thread;

import java.util.Vector;

public class ThreadPoolManager {
  private Vector<Thread> workThreadVector;
  private int workThreadVectorLen = 0;
  private static final int DEFAULT_THREAD_NUMBER=10;

  public ThreadPoolManager(int threadNum) {
    workThreadVector = new Vector<>(threadNum);
  }

  public ThreadPoolManager() {
    workThreadVector = new Vector<>(DEFAULT_THREAD_NUMBER);
  }

  public void createPool() {
    synchronized (workThreadVector) {
      for(int i=0;i<workThreadVector.size();i++){
        workThreadVectorLen++;
      }
    }
  }
}
