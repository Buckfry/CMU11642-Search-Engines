/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopSum extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchMin (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelBM25) {
      return this.getScoreBM25((RetrievalModelBM25)r);
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
    }
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }
  /**
   *  getScore for the RankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreBM25 (RetrievalModelBM25 r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      // compute score
      // inputs are scores
      int mindocid = this.docIteratorGetMatch();

      if (mindocid == INVALID_DOCID){
        return 0.0;
      }

      double sumscore = 0.0;
      int i;
      int j;
      int count;

      //count qtf
      /*
      ArrayList<Integer> qtf = new ArrayList<Integer>(this.args.size());
      ArrayList<Boolean> firstoccur = new ArrayList<Boolean>(this.args.size());

      for (i=0; i < this.args.size(); i++) {
        firstoccur.add(i, true);
      }

      for (i=0; i < this.args.size(); i++) {
        count = 1;
          for (j=i+1; j < this.args.size(); j++){
            if (this.args.get(i).toString().equals(this.args.get(j).toString())) {
              count++;
              firstoccur.set(j,false);
            }
          }
        qtf.add(i,count);
      }
      */


      for (i=0; i < this.args.size(); i++) {
        QrySop q_i = (QrySop)this.args.get(i);
        if (q_i.docIteratorHasMatchCache()){// && firstoccur.get(i)) {//first occurring
          if (mindocid == q_i.docIteratorGetMatch()){
              double k3 = r.getK3();
              double userweight = 1d;//(k3+1)*qtf.get(i)/(k3+qtf.get(i));
              sumscore += userweight*q_i.getScore(r);
          }
        }
      }
      return sumscore;
    }

  }

  public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
    if (r instanceof RetrievalModelIndri) {
      double score = 0d;
      for (Qry q_i : this.args) {
        score += ((QrySop) q_i).getDefaultScore(r, docid);
      }
      return score;
    }else{
      return 0.0;
    }
  }

}
