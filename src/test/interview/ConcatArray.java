package test.interview;

import java.lang.reflect.Array;

public class ConcatArray {
  public static void main(String[] args) {
    NArray<Integer> na1 = new NArray<Integer>(new Integer[]{1,2,3,4});
    NArray<Integer> na2 = new NArray<Integer>(new Integer[]{5,6,7,8});
    System.out.println( na1.concat(na2));
  }

  static class NArray<T extends Object> {
    private T[] value;

    @Override
    public String toString() {
      String content="";
      for (T t : value) {
        content+= t+",";
      }
      content=content.substring(0,content.length()-1);
      return "["+content+"]";
    }

    public NArray(Class<T> type,int size) {
      value = (T[]) Array.newInstance(type, size);
    }

    public NArray(T[] value) {
      this.value = value;
    }

    public T[] getValue() {
      return value;
    }

    public void setValue(T[] value) {
      this.value = value;
    }

    public NArray<T> concat(NArray<T> nArray) {
      T[] array = nArray.getValue();
      T[] result = (T[]) Array.newInstance(value.getClass().getComponentType(), value.length + array.length);
      int position = 0;
      for (int i = 0; i < value.length; i++) {
        result[position] = value[i];
        position += 1;
      }
      for (int i = 0; i < array.length;i++) {
        result[position] = array[i];
        position += 1;
      }
      return new NArray<T>(result);
    }
  }
}
