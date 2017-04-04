package edu.jhu.maxent_classifier;

import edu.jhu.util.*;
import java.util.*;

public class DataPoint
{

public String form      = null;     // segment sequence (without word boundaries)
public int[] x          = null;     // encoded segment sequence (with word boundaries)
public String attrib    = null;     // grammatical attribute (e.g., gramcat or gender) xxx TODO: extend to list attributes
public int category     = -1;		// treat '-1' as the unknown category (e.g., for test points)
public String categoryName = null;  // name of category
public double weight    = 1.0;		// scaling factor for likelihood function
public boolean[] cats   = null;     // if nonnull, cats[i]==true iff ith category is possible for this data point
                                    // if null, all categories are possible for this data point

public double[] pcat = null;	// probability of each category assignment (given constraint violations, constraint weights)
public double[] logpcat = null; // log probability of each category assignment
public HashMap<Integer,Integer> violns = new HashMap<Integer,Integer>();
	// map from constraint ids to # violns; ideally would have constraint category info here too ...

public int verbosity    = 0;

public DataPoint(String form, int[] x, String attrib, int category, double weight, boolean[] cats) {
    this.form       = form;
    this.x          = x;
    this.attrib     = attrib;
    this.category   = category;
    this.categoryName = (category>=0) ? MaxentClassifier.categoryNames[category] : "unknown";
    this.weight     = weight;
    this.cats       = cats;
}


public void init(int nCategories) {
    pcat    = new double[nCategories];
    logpcat = new double[nCategories];
}


// calculate the probability of each class (category) for this data point, given 
// the data point's constraint violations and the passed constraint weights
public void pCategory(LinkedList<ClassifierConstraint> constraints, double[] lambda) {
	Arrays.fill(logpcat,0.0);   // initially these are harmonies; converted to log probabilities below
    //System.out.println("violns: "+ violns);
	for (Map.Entry<Integer,Integer> e : violns.entrySet()) {
		int k = e.getKey();     // id of violated constraint
		int v = e.getValue();	// number of violations
		ClassifierConstraint C = constraints.get(k);	// xxx needed only for constraint category

		logpcat[C.category] -= lambda[k]*v;
        if (verbosity>5) {
            System.out.println(v +" violation(s) of constraint "+ C +" with weight "+ lambda[k]);
            System.out.println("\tlogpcat["+ C.category +"] = "+ logpcat[C.category]);
        }
    }
    if (verbosity>5)
        System.out.println("\t\tlogpcat = "+ Arrays.toString(logpcat));
    
    // zero out categories that are not possible for this data point
    if (cats!=null) {
        for (int i=(cats.length-1); i>=0; i--)
            if (!cats[i])
                logpcat[i] = Double.NEGATIVE_INFINITY;
    }
    
    //System.out.println("logpcat: "+ Arrays.toString(logpcat));
    double logZ = Double.NEGATIVE_INFINITY;
    for (int i=(pcat.length-1); i>=0; i--) {
        logZ = MathUtil.logSumExp(logZ,logpcat[i]);
    }
    if (verbosity>5)
        System.out.println("\t\tlogZ = "+ logZ);
    
    //System.out.println("logpcat = "+ Arrays.toString(logpcat) +"; logZ = "+ logZ);
    //System.out.println("logSumExp(logpcat[0],logpcat[1]) = "+ MathUtils.logSumExp(logpcat[0],logpcat[1]));
    for (int i=(pcat.length-1); i>=0; i--) {
        logpcat[i] -= logZ;
        pcat[i] = Math.exp(logpcat[i]);
        if (pcat[i]>1.0) {
            System.out.println("Error: probability greater than log(1.0)==0");
            System.out.println("data point: "+ this);
            System.out.println("violations: "+ violns);
            System.out.println("weights:    "+ Arrays.toString(lambda));
            System.out.println("logpcat:    "+ Arrays.toString(logpcat));
            System.out.println("logZ:       "+ logZ);
            System.out.println("pcat:       "+ Arrays.toString(pcat));
            System.exit(1);
        }
    }
    if (verbosity>5)
        System.out.println("\t\tpcat = "+ Arrays.toString(pcat));
}


// add constraint violation for a new constraint to violns
public void addViolation(ClassifierConstraint C) {
	int violn = C.evaluate(this);
    if (violn==0)
        return;
    violns.put(C.id,violn);
    //    { System.out.println(Arrays.toString(x) +";"+ category +" incurs "+ violn +" violation(s) of "+ C); }
}


// print this data point
public String toString() {
    StringBuffer buffer = new StringBuffer();
    buffer.append(form);
    //buffer.append(":");
    //buffer.append(Arrays.toString(x));
    if (attrib!=null) {
        buffer.append("<" + attrib +">");
    }
    buffer.append("," + categoryName);
    buffer.append("," + weight);
    return buffer.toString();
}

}
