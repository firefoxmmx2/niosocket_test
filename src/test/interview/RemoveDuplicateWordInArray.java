package test.interview;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 去除在一个有序数组里面的重复内容
 */
public class RemoveDuplicateWordInArray {

  public Integer[] removeDuplicateWordNormal(Integer[] arr) {
    Integer[] result = new Integer[arr.length];
    int position = 0;
    for (int i = 0; i < arr.length; i++) {
      boolean exist = false;
      for(int j=0;j<=position; j++){
        if(result[j] == arr[i]){
          exist=true;
          break;
        }
      }
      if(!exist){
        result[position]=arr[i];
        position+=1;
      }
    }
    Integer[] realResult= Arrays.copyOfRange(result, 0, position);
    return realResult;
  }

  public Integer[] removeDulicateWordWithSet(Integer[] arr){
    Set<Integer> clearSet = new LinkedHashSet<>();
    for (int i : arr) {
      clearSet.add(i);
    }

    return clearSet.toArray(new Integer[]{});
  }
  public static void main(String[] args) {
    Integer[] arr=new Integer[]{1,3,2,2,3,4,5,5,6};
    RemoveDuplicateWordInArray r=new RemoveDuplicateWordInArray();
    String arr1="",arr2="";
    for (Integer i : r.removeDulicateWordWithSet(arr)) {
      arr1 += i+",";
    }
    for (Integer i : r.removeDuplicateWordNormal(arr)) {
      arr2 += i+",";
    }
    System.out.println(arr1);
    System.out.println(arr2);
  }
}
