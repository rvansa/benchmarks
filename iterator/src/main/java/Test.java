import java.util.ArrayList;
import java.util.Iterator;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Test {
   public static void main(String[] args) {
      ArrayList<String> list = new ArrayList<String>();
      Iterator foo = list.iterator();
      System.out.println(foo.hasNext() );
      System.out.println("start");
      for(int i=0; i<100; i++){

         for(String val : list){

            System.out.println(val);
         }

      }

      System.out.println("stop");
   }
}
