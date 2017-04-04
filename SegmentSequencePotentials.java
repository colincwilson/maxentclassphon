package edu.jhu.maxent_classifier;

import java.util.*;
import gnu.trove.iterator.*;
import gnu.trove.map.hash.*;
import edu.jhu.util.*;
import edu.jhu.features.*;

public class SegmentSequencePotentials 
{

static int[] nNodes				= null;
static int[] nNegPotential		= null;
static double[] avgCountSize	= null;
static int[] maxCountSize		= null;
static int[] minCountSize		= null;

static int verbosity			= 0;

// calculate `potential' of each segment sequence represented by the trie w.r.t the given category
public static void getPotentials(DataPoint[] data, IntTrie segSeqTrie, int category, int maxDepth) {
	nNodes			= new int[maxDepth+1];
	nNegPotential	= new int[maxDepth+1];
	avgCountSize	= new double[maxDepth+1];
	maxCountSize	= new int[maxDepth+1];
	minCountSize	= new int[maxDepth+1];	Arrays.fill(minCountSize,Integer.MAX_VALUE);

	ArrayDeque<IntTrieNode> Q = new ArrayDeque<IntTrieNode>();
	Q.add(segSeqTrie.rtNode);
	while (!Q.isEmpty()) {
		IntTrieNode node = Q.removeFirst();
		getPotential(data,node,category);
		if (node.depth<maxDepth)
			Q.addAll(node.nodes());
		//System.out.println(node);
	}
	if (verbosity>0) {
		System.out.println("nNodes = "+ Arrays.toString(nNodes));
		System.out.println("nNegPotential = "+ Arrays.toString(nNegPotential));
		for (int i=0; i<=maxDepth; i++)
			avgCountSize[i] /= nNodes[i];
		System.out.println("avgCountSize = "+ Arrays.toString(avgCountSize));
		System.out.println("maxCountSize = "+ Arrays.toString(maxCountSize));
		System.out.println("minCountSize = "+ Arrays.toString(minCountSize));
	}
}

// calculate `potential' of a segment sequence (equiv. trie node), implicitly defined by counts: 
// \sum_{x \in D} (I[x.category==category]-p(category|x))count(x)
public static void getPotential(DataPoint[] data, IntTrieNode node, int category) {
	double value = 0.0;
	TIntIntIterator iter = node.counts().iterator();
	while (iter.hasNext()) {
		iter.advance();
		double a = (data[iter.key()].category==category) ? 1.0 : 0.0;
		double b = data[iter.key()].pcat[category];
		value += data[iter.key()].weight * (a-b) * iter.value();
	}
	node.value = value;

	nNodes[node.depth]++;
	if (value<0.0)
		nNegPotential[node.depth]++;
	int countSize = node.counts().size();
	avgCountSize[node.depth] += countSize;
	if (maxCountSize[node.depth]<countSize)
		maxCountSize[node.depth] = countSize;
	if (minCountSize[node.depth]>countSize)
		minCountSize[node.depth] = countSize;
}

}
