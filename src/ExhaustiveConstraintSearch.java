// This class exhaustively searches the constraint space, with
// non-closed patterns pruned in the forward direction (and, optionally
// in the backward direction) as well. Most of the work of determining
// closed patterns in the forward direction is delegated to
// NaturalClasses.dom() and NaturalClass.closed()

package edu.jhu.maxent_classifier;

import gnu.trove.map.hash.*;    // todo: hide backing implementation
import java.util.*;
import edu.jhu.util.*;
import edu.jhu.features.*;
import edu.jhu.fsm.*;

public class ExhaustiveConstraintSearch
{

NaturalClasses naturalClasses			= null;		// contains natural class tree
IntTrieNode rtNode						= null;		// root of segment sequence trie
int category							= 0;		// category of constraint
int maxLen								= 0;		// maximum constraint length
ArrayList<ArrayDeque<SearchState>> Q	= null;		// queue for each position 0..(maxLen-1)
HashSet<BitSet> supports				= new HashSet<BitSet>();
int nConstraints						= 0;
int nConstraints3						= 0;
double avgNodes							= 0.0;
int minNodes							= Integer.MAX_VALUE;
int maxNodes							= 0;
double avgNegPotential					= 0.0;
double minNegPotential					= 0.0;
double maxNegPotential					= -Double.MAX_VALUE;
int verbosity							= 10;

public ExhaustiveConstraintSearch(NaturalClasses naturalClasses, IntTrieNode rtNode, int category, int maxLen) {
	this.naturalClasses	= naturalClasses;
	this.rtNode			= rtNode;
	this.category		= category;
	init(maxLen);
}


public void init(int maxLen) {
	this.maxLen = maxLen;
	Q = new ArrayList<ArrayDeque<SearchState>>();
	for (int i=0; i<maxLen; i++)
		Q.add(new ArrayDeque<SearchState>());
	Q.get(0).add(new SearchState(
					null,							// empty prefix of natural classes
					naturalClasses.sigma.segs,      // all segments occur in the empty context
					naturalClasses.sigma,			// natural class containing all segments
					new ArrayList<IntTrieNode>(rtNode.nodes.valueCollection())	// all trie nodes from root
					)
				);
	//supports.clear();
	nConstraints = 0;
}


public SearchState next() {
	// search queues in reverse length order
	for (int i=(maxLen-1); i>=0; i--) {
		if (Q.get(i).isEmpty())
			continue;

		SearchState q = Q.get(i).removeFirst();
		//if (i==0) { System.out.print(".("+ nConstraints +") "); }
		//if (i==0) { System.out.print(".("+ supports.size() +") "); }
		//if (i==0) { System.out.print("."); System.out.print("("+ supports.size() +") "); }
		//if (i==2) System.out.print(q.nodes.size() +" ");
		//System.out.println(q);
		
		
		// calculate total `potential' xxx for category==0 (see MaxentClassifier call to SegmentSequencePotentials)
		double nInCategory = 0;
		double nOutCategory = 0;
		double totalPotential = 0.0;
		double totalNegPotential = 0.0;
		double totalPosPotential = 0.0;
		for (IntTrieNode node : q.nodes) {
			if (node.value<0.0) totalNegPotential += node.value;
			else totalPosPotential += node.value;
			for (int k : node.counts.keys()) {
				if (MaxentClassifier.train[k].category==category)
					nInCategory += MaxentClassifier.train[k].weight;
				else
					nOutCategory += MaxentClassifier.train[k].weight;
			}
		}
		totalPotential = (totalNegPotential + totalPosPotential);
		int len = 1 + ((q.X_==null) ? 0 : q.X_.length);

		// add constraints that differ from this one in the last class
		// (i.e., descend the natural-class tree at the final class)
		if (len<(maxLen-1) || (totalNegPotential<0.0 && totalPosPotential>0.0))
			descend(q,i);

		if (i<(maxLen-1)) {
			extend(q,i);
		}

		if (totalPotential < 0.0) {
			nConstraints++;
			if (len==3)
				nConstraints3++;
			avgNodes += q.nodes.size();
			if (q.nodes.size()<minNodes) minNodes = q.nodes.size();
			if (q.nodes.size()>maxNodes) maxNodes = q.nodes.size();
			avgNegPotential += totalPotential;
			if (totalPotential<minNegPotential) minNegPotential = totalPotential;
			if (totalPotential>maxNegPotential) maxNegPotential = totalPotential;
		}
		return q;
	}
	
	if (verbosity>0) {
		System.out.println("nConstraints = "+ nConstraints);
		System.out.println("nConstraints3 = "+ nConstraints3);
		System.out.println("avg nodes per constraint = "+ avgNodes / (double) nConstraints);
		System.out.println("min nodes per constraint = "+ minNodes);
		System.out.println("max nodes per constraint = "+ maxNodes);
		System.out.println("avg neg potential = "+ avgNegPotential / (double) nConstraints);
		System.out.println("min neg potential = "+ minNegPotential);
		System.out.println("max neg potential = "+ maxNegPotential);	
		System.out.println("number of distinct supports = "+ supports.size());
	}
	return null;
}


// descend natural class tree
public void descend(SearchState q, int i) {
	if (q.nodes.isEmpty())	// equiv., q.segs.cardinality()==0
		return;

	// iterate over maximal `closed' classes dominated 
	// by the final natural class of this pattern
    for (NaturalClass Z : naturalClasses.naturalClassTree.get(q.Y)) {
		SearchState r = new SearchState(
			q.X_, q.segs, Z, new ArrayList<IntTrieNode>() );
		for (IntTrieNode node : q.nodes)
			if (Z.segs.get(node.id))
				r.nodes.add(node);
		if (!r.nodes.isEmpty())
			Q.get(i).add(r);
		//System.out.println("\tr = "+ r.Y.segs);
	}
}


// extend constraint by adding root of natural class tree
public void extend(SearchState q, int i) {
	if (q.nodes.isEmpty())
		return;
	SearchState r = new SearchState(
		concat(q.X_,q.Y), new BitSet(), naturalClasses.sigma, new ArrayList<IntTrieNode>() );
	for (IntTrieNode node_i : q.nodes) {				// xxx would be faster if node_js were organized by segment
		for (IntTrieNode node_j : node_i.nodes()) {
			r.segs.set(node_j.id);
			r.nodes.add(node_j);
		}
	}
	if (!r.nodes.isEmpty())
		Q.get(i+1).add(r);
}


// append Y to X_
public NaturalClass[] concat(NaturalClass[] X_, NaturalClass Y) {
	if (X_==null)
		return new NaturalClass[]{Y};
	NaturalClass[] Z_ = Arrays.copyOf(X_,X_.length+1);
	Z_[X_.length] = Y;
	return Z_;
}


// apply backward pruning to the constraint corresponding to the 
// given search state
public boolean backwardBlocked(SearchState q) {
	// does not apply to constraints of length 1
	if (q.X_==null)
		return false;

	int len = q.X_.length;
	NaturalClass[] XY = Arrays.copyOf(q.X_,len+1); XY[len] = q.Y;

	ArrayList<IntTrieNode> prefixNodes	= null;
	ArrayList<IntTrieNode> currentNodes	= new ArrayList<IntTrieNode>();
	BitSet segs							= new BitSet();
	for (int posn=0; posn<=len; posn++) {
		prefixNodes = getNodes(XY,posn-1);
		currentNodes.clear();
		for (IntTrieNode node : prefixNodes)
			currentNodes.addAll(node.nodes.valueCollection());
		segs.clear();
		for (IntTrieNode node : currentNodes)
			if (checkSuffix(node,XY,posn))
				segs.set(node.id);
		if (!NaturalClassUtil.closed(XY[posn],segs))
			return false;
	}
	return true;
}

// xxx should be relocated
public ArrayList<IntTrieNode> getNodes(NaturalClass[] X, int prefixLen) {
	ArrayList<IntTrieNode> nodes	= new ArrayList<IntTrieNode>();
	ArrayList<IntTrieNode> nexts	= new ArrayList<IntTrieNode>();
	ArrayList<IntTrieNode> tmp		= null;
	nodes.add(rtNode);
	for (int i=0; i<prefixLen; i++) {
		for (IntTrieNode node : nodes)
			for (IntTrieNode next : node.nodes())
				if (X[i].segs.get(next.id))
					nexts.add(next);
		tmp = nodes; nodes = nexts; nexts = nodes; nexts.clear();
	}
	return nodes;
}

// xxx should be relocated
public boolean checkSuffix(IntTrieNode node, NaturalClass[] X, int prefixLen) {
	ArrayList<IntTrieNode> nodes	= new ArrayList<IntTrieNode>();
	ArrayList<IntTrieNode> nexts	= new ArrayList<IntTrieNode>();
	ArrayList<IntTrieNode> tmp		= null;
	nodes.add(node);
	int len = X.length;
	for (int i=(prefixLen+1); i<len; i++) {
		for (IntTrieNode myNode : nodes)
			for (IntTrieNode next : myNode.nodes())
				if (X[i].segs.get(next.id))
					nexts.add(next);
		tmp = nodes; nodes = nexts; nexts = nodes; nexts.clear();
	}
	return (!nodes.isEmpty());
}

}
