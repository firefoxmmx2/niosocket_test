package test.interview;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ArraysIntersection {
  static class NArray<T extends Object> {
    private T[] value;

    public T[] getValue() {
      return value;
    }

    public void setValue(T[] value) {
      this.value = value;
    }

    public NArray(T[] value) {
      this.value = value;
    }

    public NArray(Class type,int size) {
      value = (T[]) Array.newInstance(type,size);
    }

    public NArray<T> intersect(NArray<T> array) {
      List<T> result = new ArrayList<>();
      T[] a=array.getValue();
      for (int i = 0; i < value.length; i++) {
        for (int j = 0; j < a.length; j++) {
          if(value[i].equals(a[i]) && !result.contains(value[i]))
            result.add(value[i]);
        }
      }
      return new NArray(result.toArray());
    }

    public NArray<T> intersectAll(Collection<NArray<T>> collection) {
      if(collection == null && collection.size() == 0)
        throw new RuntimeException("交集集合不能为空集");

      NArray<T> resultArray=collection.iterator().next();
      for (NArray<T> array : collection) {
        resultArray = resultArray.intersect(array);
      }
      return resultArray;
    }

    @Override
    public String toString() {
      String content = "";
      for (int i = 0; i < value.length; i++) {
        T t = value[i];
        content += t+",";
      }
      content = content.substring(0,content.length()-1);
      return "["+content+"]";
    }
  }

  private NArray<Integer> na1=new NArray<Integer>(new Integer[]{1,2,3,5,6,7,8});
  private NArray<Integer> na2=new NArray<Integer>(new Integer[]{1,5,9});
  private NArray<Integer> na3=new NArray<Integer>(new Integer[]{1,5,2});

  public void testIntersect(){
    List<NArray<Integer>> l=new ArrayList<>();
    l.add(na2);
    l.add(na3);

    System.out.println(na1.intersectAll(l));
  }
  public static void main(String[] args) {
    ArraysIntersection ai=new ArraysIntersection();
    ai.testIntersect();
  }
}
