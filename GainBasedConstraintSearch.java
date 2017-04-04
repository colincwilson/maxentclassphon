package edu.jhu.maxent_classifier;

import java.util.*;
import edu.jhu.util.*;
import edu.jhu.features.*;
import edu.jhu.fsm.*;
import edu.jhu.maxent.*;


public class GainBasedConstraintSearch
{
MaxentClassifier classifier         = null;
Projection projection               = null;
TrieBasedConstraintIterator iter    = null;
ClassifierConstraintGainEvaluator eval	= null;
int nCategories						= -1;

IntTrieNode bestConstraintNode		= null;
double bestGain						= 0.0;
int bestCategory					= -1;	
double gain							= 0.0;
boolean binaryConstraints           = false;

int verbosity = 0;

public GainBasedConstraintSearch(MaxentClassifier classifier, Projection projection, ClassifierConstraintGainEvaluator eval, int nCategories) {
    this.classifier     = classifier;
	this.projection		= projection;
	this.eval			= eval;
	this.nCategories	= nCategories;
    resetIterator();
}
    
    
public void resetIterator() {
    iter = new TrieBasedConstraintIterator(
                    projection.conTrie,
                    projection.corpusIndexer,
                    null,
                    projection.naturalClasses.naturalClasses,
                    projection.maxConLength
                );
}


public ClassifierConstraint apply(int maxLen, LinkedList<String> attribs) {
	bestConstraintNode	= null;
	bestGain			= MaxentClassifier.minGain;
	bestCategory		= -1;
    binaryConstraints   = MaxentClassifier.binaryConstraints;
	iter.init(maxLen);

    ClassifierConstraint bestConstraint = null;
	while (true) {
		iter.next();
		if (iter.constraintNode==null)
            break;
            
        for (String attrib : attribs) {
            for (int category=0; category<nCategories; category++) {
                // evaluate violation-counting version of constraint
                gain = eval.apply(iter.segsObserve, attrib, category);
                if (verbosity>9) System.out.println(iter.constraintNode +";"+ category +" -> "+ gain);
                if (gain>bestGain) {
                    ClassifierConstraint C = new ClassifierConstraint(projection,nodeToPattern(iter.constraintNode),attrib,category);
                    // reject C if it is already in the grammar (should happen only if regularizer is very strong)
                    if (classifier.constraints.contains(C))
                        continue;
                    bestGain				= gain;
                    bestConstraintNode		= iter.constraintNode;
                    bestCategory			= category;
                    bestConstraint          = C;
                    System.out.println("** "+ bestConstraintNode +"<" + attrib +">(depth="+ bestConstraintNode.depth +") gain="+bestGain);
                }
                
                // evaluate binary version of constraint
                if (!binaryConstraints)
                    continue;
                gain = eval.applyBinary(iter.segsObserve, attrib, category);
                if (verbosity>9) System.out.println(iter.constraintNode +";"+ category +" -> "+ gain);
                if (gain>bestGain) {
                    BinaryClassifierConstraint C = new BinaryClassifierConstraint(projection,nodeToPattern(iter.constraintNode),attrib,category);
                    if (classifier.constraints.contains(C))
                        continue;
                    bestGain                = gain;
                    bestConstraintNode      = iter.constraintNode;
                    bestCategory            = category;
                    bestConstraint          = C;
                    System.out.println("** "+ bestConstraintNode +"<" + attrib +">(depth="+ bestConstraintNode.depth +") gain="+bestGain);
                }
            }
        }
	}
    return bestConstraint;
}


// xxx move to NaturalClasses code
NaturalClass[] nodeToPattern(IntTrieNode node) {
	NaturalClass[] pattern = new NaturalClass[node.depth];
	for (int i=(node.depth-1); i>=0; i--) {
		pattern[i] = projection.naturalClasses.naturalClasses[node.id];
		node = node.pred;
	}
    return pattern;
}

}
