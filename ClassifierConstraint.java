// constraint that evaluates forms w.r.t. membership in a particular class/category

package edu.jhu.maxent_classifier;

import java.util.*;
import edu.jhu.util.*;
import edu.jhu.features.*;
import edu.jhu.fsm.*;

public class ClassifierConstraint
{

public Projection projection    = null;
public NaturalClass[] pattern   = null;
public String attrib            = null; // xxx TODO: extend to vector attributes
public String categoryName      = null;
public int category             = -1;
public int id                   = 0;

public ClassifierConstraint(Projection projection, NaturalClass[] pattern, String attrib, int category) {
    this.projection = projection;
    this.pattern    = (pattern!=null) ? Arrays.copyOf(pattern,pattern.length) : null;
    this.attrib     = attrib;
    this.category   = category;
    this.categoryName = MaxentClassifier.categoryNames[category];
}


// returns number of violations of this constraint 
// in data point x
public int evaluate(DataPoint form) {
    // check for matching attributes
    if (attrib!=null && !form.attrib.equals(attrib)) {
        return 0;
    }
    // evaluate form on projection for this constraint
    projection.project(form.x);
	return evaluate(pattern, projection.projectedForm, projection.projectedLength);
}


// returns number of instances of pattern in form x
public static int evaluate(NaturalClass[] pattern, int[] x, int len) {
	// special case: length-0 pattern -> every form violates the constraint once
	// (this is useful for definining `baseline' constraints that penalize 
	// categories independently of the form)
	if (pattern==null || pattern.length==0)
		return 1;

	int patternLength = pattern.length;
	if (len<patternLength) return 0;
	int value = 0;
	for (int start=0; start<(len-patternLength+1); start++) {
		int j = 0;	// position in pattern (and offset from start in x)
		while (j<patternLength && (start+j)<len) {
			if (pattern[j].segs.get(x[start+j]))
				j++;	// advance position in pattern
			else
				break;	// no match beginning at start
		}
		if (j==patternLength)	// found a match ...
			value++;			// increase count of matches
	}
	return value;
}


// returns the empirical 'closure' of this constraint -- the longest 
// pattern that induces the same data point -> violation count mapping 
// (determined greedily by first extending the pattern forward and then 
// backward)
// xxx todo: check
public ClassifierConstraint closure() {
    if (pattern==null)
        return this;
        
    if (projection.corpusIndexer==null)
        return null;

    LinkedList<NaturalClass> closure = new LinkedList<NaturalClass>();
    for (int i=0; i<pattern.length; i++) {
        closure.add(pattern[i]);
    }
	
    ArrayList<IntTrieNode> oldNodes = new ArrayList<IntTrieNode>();
    ArrayList<IntTrieNode> newNodes = new ArrayList<IntTrieNode>();
    ArrayList<IntTrieNode> tmpNodes = null;
    oldNodes.add(projection.corpusIndexer.rtNode);
    for (int i=0; i<pattern.length; i++) {
        newNodes.clear();
        for (IntTrieNode nodei : oldNodes) {
            for (IntTrieNode nodej : nodei.nodes()) {
                if (pattern[i].segs.get(nodej.id))
                    newNodes.add(nodej);
            }
        }
        oldNodes.clear();
        tmpNodes = oldNodes; oldNodes = newNodes; newNodes = tmpNodes;
    }

    // forward closure
    BitSet nextSegments = new BitSet();
    while (true) {
        nextSegments.clear();
        for (IntTrieNode nodei : oldNodes) {
            if (nodei.nodes().isEmpty()) {
                nextSegments.clear();
                break;
            }
            for (IntTrieNode nodej : nodei.nodes()) {
                nextSegments.set(nodej.id);
                newNodes.add(nodej);
            }
        }
        if (nextSegments.cardinality()==0) {
            break;
        }
        NaturalClass nextClass = projection.naturalClasses.get(nextSegments);
        if (nextClass==null) {
            break;
        }
        closure.add(nextClass);
        oldNodes.clear();
        tmpNodes = oldNodes; oldNodes = newNodes; newNodes = tmpNodes;
    }
    
    // xxx todo: add backward closure
	
    ClassifierConstraint C = new ClassifierConstraint(projection, closure.toArray(new NaturalClass[0]), attrib, category);
    return C;
}

public boolean equals(Object o) {
    if (o==null) return false;
    if (o==this) return true;
    if (!(o instanceof ClassifierConstraint)) return false;
    ClassifierConstraint C = (ClassifierConstraint) o;
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
    buffer.append("*");
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
    buffer.append("*");
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
