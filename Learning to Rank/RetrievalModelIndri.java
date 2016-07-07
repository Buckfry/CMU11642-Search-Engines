/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {
  protected double mu;
  protected double lambda;

  public double getLambda() {
    return lambda;
  }

  public double getMu() {
    return mu;
  }

  public void setLambda(double lambda) {
    this.lambda = lambda;
  }

  public void setMu(double mu) {
    this.mu = mu;
  }
  public String defaultQrySopName () {
    return new String ("#and");
  }

}
