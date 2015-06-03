package test.interview;

/**
 * 用递归反转字符串
 */
public class RecuriveInverseWord {
  public String inverseWord(String word){
    return inverse(word);
  }
  private String inverse(String subword){
    if(subword != null && subword.length() == 1)
      return subword;
    else{
      return subword.charAt(subword.length()-1) + inverse(subword.substring(0,subword.length()-1));
    }
  }
  public static void main(String[] args) {
    RecuriveInverseWord recuriveInverseWord=new RecuriveInverseWord();
    System.out.println(recuriveInverseWord.inverseWord("hello"));
  }
}
