# LawOfDemeter
**Category:** `pmd`<br/>
**Rule Key:** `pmd:LawOfDemeter`<br/>


-----

The Law of Demeter is a simple rule, that says "only talk to friends". It helps to reduce coupling between classes or objects.
See also the references:
Andrew Hunt, David Thomas, and Ward Cunningham. The Pragmatic Programmer. From Journeyman to Master. Addison-Wesley Longman, Amsterdam, October 1999.;
K.J. Lieberherr and I.M. Holland. Assuring good style for object-oriented programs. Software, IEEE, 6(5):38–48, 1989.;
http://www.ccs.neu.edu/home/lieber/LoD.html;
http://en.wikipedia.org/wiki/Law_of_Demeter
<p>Example:</p>
<pre>
public class Foo {
    /**
     * This example will result in two violations.
     */
    public void example(Bar b) {
        // this method call is ok, as b is a parameter of "example"
        C c = b.getC();
        
        // this method call is a violation, as we are using c, which we got from B.
        // We should ask b directly instead, e.g. "b.doItOnC();"
        c.doIt();
        
        // this is also a violation, just expressed differently as a method chain without temporary variables.
        b.getC().doIt();
        
        // a constructor call, not a method call.
        D d = new D();
        // this method call is ok, because we have create the new instance of D locally.
        d.doSomethingElse(); 
    }
}
</pre>
