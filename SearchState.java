// encapsulates search state for use by ExhaustiveConstraintSearch

package edu.jhu.maxent_classifier;

import java.util.*;
import edu.jhu.util.*;
import edu.jhu.features.*;
import edu.jhu.fsm.*;


public class SearchState
{

public NaturalClass[] X_			= null;	// natural-class sequence prefix (possibly empty)
public BitSet segs					= null;	// segments that appear in X_ in the data set
public NaturalClass Y				= null;	// natural class
public ArrayList<IntTrieNode> nodes = null;	// set of all trie nodes corresponding to segment tuples 
											// that are in the extension of X_ + Y

public SearchState(NaturalClass[] X_, BitSet segs, NaturalClass Y, ArrayList<IntTrieNode> nodes) {
	this.X_		= X_;
	this.segs	= segs;
	this.Y		= Y;
	this.nodes	= nodes;
}


public String toString() {
	return "<"+ (X_==null ? X_ : Arrays.toString(X_)) +"; "+ Y +"; "+ nodes.size() +">";
}

}
