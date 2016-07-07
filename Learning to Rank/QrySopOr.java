/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

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

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
      return this.getScoreRankedBoolean (r);
    } else if (r instanceof RetrievalModelIndri) {
      return this.getScoreIndri(r);
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
  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      // compute score
      // inputs are scores
      int mindocid = this.docIteratorGetMatch();
      double maxscore = 0.0;
      double scorecache;
      for (Qry q_i: this.args) {
        scorecache = 0.0;
        if (q_i.docIteratorHasMatchCache()) {
            if (mindocid == q_i.docIteratorGetMatch()){
              scorecache = ((QrySop) q_i).getScore(r);
            if (scorecache > maxscore) {
              maxscore = scorecache;
            }
          }
        }
      }
      //System.out.println();
      //System.out.println(this.docIteratorGetMatch());
      //System.out.println("maxscore:" + maxscore);
      return maxscore;
    }
  }
  public double getScoreIndri (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      int docid = this.docIteratorGetMatch();
      double score = 1d;
      for (Qry q_i : this.args) {
        if (!q_i.docIteratorHasMatchCache())
          score *= 1 - ((QrySop) q_i).getDefaultScore(r, docid);
        else if (q_i.docIteratorGetMatch() == docid)
          score *= 1 - ((QrySop) q_i).getScore(r);
        else
          score *= 1 - ((QrySop) q_i).getDefaultScore(r, docid);
      }
      score = 1 - score;
      return score;
    }
  }
  public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
    if (r instanceof RetrievalModelIndri) {
      double score = 1d;
      for (Qry q_i : this.args){
        score *= 1 - ((QrySop) q_i).getDefaultScore(r, docid);
      }
      score = 1 - score;
      return score;
    }else{
      return 0.0;
    }
  }
}
