/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {
  protected double k1;
  protected double b;
  protected double k3;

  public double getK1() {
    return k1;
  }

  public double getB() {
    return b;
  }

  public double getK3() {
    return k3;
  }

  public void setB(double b) {
    this.b = b;
  }

  public void setK1(double k1) {
    this.k1 = k1;
  }

  public void setK3(double k3) {
    this.k3 = k3;
  }

  public String defaultQrySopName () {
    return new String ("#sum");
  }

}
