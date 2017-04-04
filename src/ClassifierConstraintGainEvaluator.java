// estimate maximum possible gain of a class constraint
//	- internally, constraint weights are non-positive (cf. non-negative externally)
//	- estimated gain is maximized with the Newton-Raphson method
package edu.jhu.maxent_classifier;

import java.util.*;
import gnu.trove.iterator.*;
import gnu.trove.map.hash.*;
import edu.jhu.util.*;

public class ClassifierConstraintGainEvaluator
{

// maximization parameters
static double TOLERANCE		= 10e-6;
static double MIN_LAMBDA	= -20.0;
static int ITER_MAX			= 20;

int[] fx_all		= null;
int[] fx			= null;
double[] I_alpha	= null;
double[] p_alpha	= null;
double[] weight		= null;
int max_fx			= 0;
int nDataPoints		= 0;
double[] betaPowers = null;

int nDataPoints_MAX = 0;	// size of the arrays above

double l1 = 1.0;
double l2 = 1.0;

public int verbosity = 0;

public ClassifierConstraintGainEvaluator(double l1, double l2) {
	this.l1 = l2;   // xxx not used
	this.l2 = l2;   // xxx not used
}

public double apply(ArrayList<IntTrieNode> nodes, String attrib, int category) {
    return apply(nodes, attrib, category, false);
}

public double applyBinary(ArrayList<IntTrieNode> nodes, String attrib, int category) {
    return apply(nodes, attrib, category, true);
}

public double apply(ArrayList<IntTrieNode> nodes, String attrib, int category, boolean binaryConstraint) {
	// ensure array size
	nDataPoints = MaxentClassifier.nDataPoints;
	if (fx_all==null || fx_all.length<nDataPoints) {
		fx_all			= new int[nDataPoints];
		fx				= new int[nDataPoints];
		I_alpha			= new double[nDataPoints];
		p_alpha			= new double[nDataPoints];
		weight			= new double[nDataPoints];
	} else {
		Arrays.fill(fx_all,0);
	}
	
	// calculate number of constraint violations (fx) for all data points
	for (IntTrieNode node : nodes) {
        for (TIntIntIterator iter=node.counts.iterator(); iter.hasNext(); ) {
            iter.advance();
            if (binaryConstraint) {
                if (iter.value()>0)
                    fx_all[iter.key()] = 1;
                continue;
            }
            fx_all[iter.key()] += iter.value();
		}
	}

	// determine data subset relevant for gain calculation
	max_fx      = 0;
	nDataPoints = 0;
	
	double logProbLoss          = 0.0;	// summed log weighted prob[category] of data points in the category
	double logProbGain          = 0.0;	// summed log weighted (1-prob[category]) of data points not in the category
	double nDataPointsIn        = 0.0;
	double nDataPointsOut       = 0.0;
	for (int i=0; i<MaxentClassifier.nDataPoints; i++) {
        // data points that do not violate the constraint are irrelevant
		if (fx_all[i]==0)
			continue;

        // data points with attributes that mismatch the constraint's attribute are irrelevant
        if (attrib!=null && !MaxentClassifier.train[i].attrib.equals(attrib))
            continue;
        
        // all other data points are relevant
		fx[nDataPoints]			= fx_all[i];
		I_alpha[nDataPoints]	= (MaxentClassifier.train[i].category==category) ? 1.0 : 0.0;
		p_alpha[nDataPoints]	= MaxentClassifier.train[i].pcat[category];
		weight[nDataPoints]		= MaxentClassifier.train[i].weight;
		if (fx_all[i]>max_fx)
			max_fx = fx_all[i];

		if (I_alpha[nDataPoints]==1.0) {
			logProbLoss += weight[nDataPoints]*Math.log(p_alpha[nDataPoints]);
			nDataPointsIn++;
		}
		else {
			logProbGain += weight[nDataPoints]*Math.log(1.0-p_alpha[nDataPoints]);
			nDataPointsOut++;
		}
		nDataPoints++;
	}
	if (verbosity>9) {
		System.out.println("\tnDataPoints = "+ nDataPoints +" (in="+ nDataPointsIn +", out="+ nDataPointsOut +")");
		System.out.println("\tfx =          "+ Arrays.toString(Arrays.copyOf(fx,nDataPoints)));
		System.out.println("\tI_alpha =     "+ Arrays.toString(Arrays.copyOf(I_alpha,nDataPoints)));
		System.out.println("\tp_alpha =     "+ Arrays.toString(Arrays.copyOf(p_alpha,nDataPoints)));
		System.out.println("\tweight =      "+ Arrays.toString(Arrays.copyOf(weight,nDataPoints)));
		System.out.println("\tlogProbLoss = "+ logProbLoss);
		System.out.println("\tlogProbGain = "+ logProbGain);
		System.out.println("\tlogProbsIn =  xxx");
        System.out.println("\t-----");
	}

	double gain = calculateGain();
	return gain;
}

// gain calculation
// note: lambda <= 0 is the optimized weight, 0 < beta <= 1 is exp(lambda)
// xxx to do: add contribution of regularizers
public double calculateGain() {
    // initialize weight (and integral powers) of new constraint
	double lambda = 0.0;
	if (betaPowers==null || betaPowers.length<(max_fx+1))
		betaPowers = new double[max_fx+1];
    for (int k=0; k<(max_fx+1); k++)
        betaPowers[k] = 1.0;  // exp(lambda)^k = 1^k = 1
    double lambda_old = 0.0;
    double lambda_delta = 0.0;

	// optimize weight of new constraint
	for (int iter=0; iter<ITER_MAX; iter++) {
		// compute first and second derivative of logP(data) w.r.t. lambda
		double D1 = 0.0;
		double D2 = 0.0;
		for (int i=(nDataPoints-1); i>=0; i--) {
			if (fx[i]==0)	// xxx should never be true
				continue;

			// contribution of ith data point to first derivative
			double A = (I_alpha[i]*fx[i]);
			double B = (p_alpha[i]*betaPowers[fx[i]]*fx[i]) / (p_alpha[i]*betaPowers[fx[i]] + (1.0-p_alpha[i]));
			D1 += weight[i]*(A - B);

			// contribution of ith data point to second derivative
			D2 += weight[i]*(B*B - B*fx[i]);
		}
        
        // contribution of first and second derivative of regularizers w.r.t. lambda
        D1 -= -l1;
        D1 += 2*l2*lambda;
        D2 -= 2*l2;
		
		if (verbosity>5) {
			System.out.println("lambda = "+ lambda +", D1 = "+ D1 +", D2 = "+ D2);
		}

		// Newton-Raphson update to maximize gain
		// xxx ignoring contribution of regularizers
		lambda_old = lambda;
		lambda = (lambda - D1/D2);
		
		// check for positive weight value
		if (lambda>0.0 || Double.isNaN(lambda))
			break;
			
		// enforce lower bound
		if (lambda<MIN_LAMBDA) {
			lambda = MIN_LAMBDA;
		}
        
		// precompute integral powers of beta == exp(lambda)
		betaPowers[0] = 1.0;				// beta^0 = 1, for all lambda
		betaPowers[1] = Math.exp(lambda);	// beta^1 = exp(lambda)
		for (int i=2; i<(max_fx+1); i++)	// beta^i = beta^{i-1} * beta, i>1
			betaPowers[i] = betaPowers[i-1]*betaPowers[1];

       // check for numerical convergence of lambda
	   // xxx alternative: check for convergence of first derivative
		lambda_delta = (lambda - lambda_old);
		if (lambda_delta < 0)
			lambda_delta = -lambda_delta;
		if (lambda_delta < TOLERANCE)
			break;
	}
	if (verbosity>9) {
		System.out.println("\tlambda* = "+ lambda);
		System.out.println("\tbeta*^k = "+ Arrays.toString(betaPowers));
	}

	if (lambda>=0.0)
		return 0.0;

	// return gain value at optimized weight of new constraint
	// note: does not include log prob of data according to current grammar,
    // which is computed separately (immed. prior to search for new constraint)
    // note: does not include the contributions of regularizers
	double gain = 0.0;
	for (int i=0; i<nDataPoints; i++) {
		double A = I_alpha[i]*lambda*fx[i];
		double B = Math.log(p_alpha[i]*betaPowers[fx[i]] + (1.0-p_alpha[i]));
		gain += weight[i]*(A - B);
	}
	
	if (verbosity>9) {
		System.out.println("\tgain* = "+ gain);
	}
	return gain;
}

}
