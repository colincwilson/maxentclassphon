// constraint that evaluates forms w.r.t. membership in a particular class (aka category),
// and that assigns 0 (no violation) or 1 (one or more violations) to each form

package edu.jhu.maxent_classifier;

import java.util.*;
import edu.jhu.util.*;
import edu.jhu.features.*;
import edu.jhu.fsm.*;

public class BinaryClassifierConstraint extends ClassifierConstraint
{

public BinaryClassifierConstraint(Projection projection, NaturalClass[] pattern, String attrib, int category) {
    super(projection, pattern, attrib, category);
}

public int evaluate(DataPoint form) {
    return (super.evaluate(form) > 0) ? 1 : 0;
}

public boolean equals(Object o) {
    if (o==null) return false;
    if (o==this) return true;
    if (!(o instanceof BinaryClassifierConstraint)) return false;
    BinaryClassifierConstraint C = (BinaryClassifierConstraint) o;
    if (C.projection!=projection) return false;
    if (C.attrib!=attrib) return false;
    if (C.category!=category) return false;
    if ((C.pattern==null)!=(pattern==null)) return false;
    return Arrays.equals(pattern,C.pattern);
}

// print this constraint with natural classes
public String toString() {
    StringBuffer buffer = new StringBuffer();
    buffer.append(projection.name);
    buffer.append("\t");
    buffer.append("¬");
    if (pattern!=null) {
        for (int i=0; i<pattern.length; i++) {
            buffer.append(pattern[i]);
        }
    } else {
        buffer.append("[null]");
    }
    if (attrib!=null) {
        buffer.append("<"+ attrib +">");
    }
    buffer.append("->");
    buffer.append(categoryName);
    return buffer.toString();
}

// print this constraint with natural-class extensions
public String toExtentString() {
    StringBuffer buffer = new StringBuffer();
    buffer.append(projection.name);
    buffer.append("\t");
    buffer.append("¬");
    if (pattern!=null) {
        for (int i=0; i<pattern.length; i++) {
            buffer.append("( ");
            for (int k=pattern[i].segs.nextSetBit(0); k!=-1; k=pattern[i].segs.nextSetBit(k+1)) {
                buffer.append(projection.getAlphabet().syms.get(k)+ " ");
            }
            buffer.append(")");
        }
    } else {
        buffer.append("()");
    }
    if (attrib!=null) {
        buffer.append("<"+ attrib +">");
    }
    buffer.append("->");
    buffer.append(categoryName);
    return buffer.toString();
}

}
